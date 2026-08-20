package com.aidevos.orchestrator.execution;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExecutionGuardrailsTest {

	@Test
	void defaultsApplyWhenMetadataIsEmpty() {
		ExecutionLimits limits = ExecutionLimits.resolve(Map.of());
		assertEquals(20, limits.maxTotalAttempts());
		assertEquals(10, limits.maxAiAttempts());
		assertEquals(10, limits.maxToolAttempts());
		assertEquals(2, limits.maxRepairAttempts());
		assertEquals(2, limits.maxReplanAttempts());
		assertEquals(3, limits.maxConsecutiveFailures());
	}

	@Test
	void resolveReadsCustomMetadataValues() {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("maxTotalAttempts", 4);
		metadata.put("maxRepairAttempts", 1);
		metadata.put("maxReplanAttempts", 1);
		metadata.put("maxToolAttempts", "2");
		ExecutionLimits limits = ExecutionLimits.resolve(metadata);
		assertEquals(4, limits.maxTotalAttempts());
		assertEquals(1, limits.maxRepairAttempts());
		assertEquals(1, limits.maxReplanAttempts());
		assertEquals(2, limits.maxToolAttempts());
	}

	@Test
	void exceededReportsFirstExceededLimit() {
		ExecutionLimits limits = ExecutionLimits.resolve(Map.of("maxTotalAttempts", 2,
			"maxToolAttempts", 1, "maxAiAttempts", 3));
		RunExecutionState state = new RunExecutionState("run-1");
		state.incrementToolAttempts();
		assertEquals("maxToolAttempts", limits.exceeded(state));
		state.incrementTotalAttempts();
		state.incrementTotalAttempts();
		assertEquals("maxTotalAttempts", limits.exceeded(state));
		state.incrementAiAttempts();
		state.incrementAiAttempts();
		state.incrementAiAttempts();
		assertEquals("maxTotalAttempts", limits.exceeded(state));
	}

	@Test
	void notExceededWhenCountersBelowCeilings() {
		RunExecutionState state = new RunExecutionState("run-1");
		assertNull(ExecutionLimits.defaults().exceeded(state));
		state.incrementTotalAttempts();
		state.incrementAiAttempts();
		state.incrementConsecutiveFailures();
		assertNull(ExecutionLimits.defaults().exceeded(state));
	}

	@Test
	void severityAndResponseMapPerLadder() {
		assertEquals(FailureSeverity.L0_RECOVERABLE,
			FailureClassifier.severity(FailureClass.NETWORK_ERROR));
		assertEquals(FailureSeverity.L0_RECOVERABLE,
			FailureClassifier.severity(FailureClass.HEALTH_CHECK_FAILED));
		assertEquals(FailureSeverity.L1_AI_RECOVERABLE,
			FailureClassifier.severity(FailureClass.BUILD_FAILED));
		assertEquals(FailureSeverity.L3_HUMAN_REQUIRED,
			FailureClassifier.severity(FailureClass.USAGE_LIMIT));
		assertEquals(FailureSeverity.L2_HUMAN_DECISION,
			FailureClassifier.severity(FailureClass.GIT_CONFLICT));
		assertEquals(FailureSeverity.L3_HUMAN_REQUIRED,
			FailureClassifier.severity(FailureClass.CREDENTIAL_MISSING));
		assertEquals(FailureSeverity.L4_SYSTEM_FAILURE,
			FailureClassifier.severity(FailureClass.STATE_CORRUPTION));
		assertEquals(FailureResponse.RETRY_TOOL,
			FailureClassifier.response(FailureClass.NETWORK_ERROR));
		assertEquals(FailureResponse.REQUEST_HUMAN,
			FailureClassifier.response(FailureClass.USAGE_LIMIT));
		assertEquals(FailureResponse.RETRY_AI,
			FailureClassifier.response(FailureClass.TEST_FAILED));
		assertEquals(FailureResponse.REPLAN_AI,
			FailureClassifier.response(FailureClass.CODE_LOGIC_ERROR));
		assertEquals(FailureResponse.REQUEST_HUMAN,
			FailureClassifier.response(FailureClass.APPROVAL_REQUIRED));
		assertEquals(FailureResponse.STOP,
			FailureClassifier.response(FailureClass.DATABASE_UNAVAILABLE));
	}

	@Test
	void onlyL0ClassesAreToolRetryable() {
		assertEquals(true, FailureClassifier.isRetryable(FailureClass.NETWORK_ERROR));
		assertEquals(false, FailureClassifier.isRetryable(FailureClass.EXECUTOR_FAILED));
		assertEquals(false, FailureClassifier.isRetryable(FailureClass.GIT_CONFLICT));
		assertEquals(false, FailureClassifier.isRetryable(FailureClass.CREDENTIAL_MISSING));
		assertEquals(false, FailureClassifier.isRetryable(FailureClass.STATE_CORRUPTION));
		assertEquals(false, FailureClassifier.isRetryable(FailureClass.USAGE_LIMIT));
	}

	@Test
	void recommendedActionMapsToHumanGuidance() {
		assertEquals(RecommendedAction.CHECK_NETWORK, FailureClassifier.recommendedAction(
			FailureClass.NETWORK_ERROR, FailureResponse.RETRY_TOOL));
		assertEquals(RecommendedAction.FIX_CREDENTIAL, FailureClassifier.recommendedAction(
			FailureClass.CREDENTIAL_MISSING, FailureResponse.REQUEST_HUMAN));
		assertEquals(RecommendedAction.REVIEW_CODE, FailureClassifier.recommendedAction(
			FailureClass.BUILD_FAILED, FailureResponse.RETRY_AI));
		assertEquals(RecommendedAction.ABORT, FailureClassifier.recommendedAction(
			FailureClass.STATE_CORRUPTION, FailureResponse.STOP));
		assertEquals(RecommendedAction.RETRY_MANUALLY, FailureClassifier.recommendedAction(
			FailureClass.USAGE_LIMIT, FailureResponse.REQUEST_HUMAN));
		assertEquals(RecommendedAction.REPLAN, FailureClassifier.recommendedAction(
			FailureClass.CODE_LOGIC_ERROR, FailureResponse.REPLAN_AI));
	}

	@Test
	void classifyMessageMapsKeywordsWithoutLlm() {
		assertEquals(FailureClass.TEST_FAILED,
			FailureClassifier.classifyMessage("Tests run: 3, Failures: 1"));
		assertEquals(FailureClass.BUILD_FAILED,
			FailureClassifier.classifyMessage("BUILD FAILURE: cannot find symbol"));
		assertEquals(FailureClass.GIT_CONFLICT,
			FailureClassifier.classifyMessage("Your local changes would be overwritten by merge"));
		assertEquals(FailureClass.CREDENTIAL_MISSING,
			FailureClassifier.classifyMessage("authentication failed"));
		assertEquals(FailureClass.NETWORK_ERROR,
			FailureClassifier.classifyMessage("connection refused"));
		assertEquals(FailureClass.STATE_CORRUPTION,
			FailureClassifier.classifyMessage("state corruption detected"));
		assertEquals(FailureClass.DATABASE_UNAVAILABLE,
			FailureClassifier.classifyMessage("database unavailable"));
		assertEquals(FailureClass.UNKNOWN,
			FailureClassifier.classifyMessage("something unexpected happened"));
	}
}
