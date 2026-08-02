package com.aidevos.orchestrator.planner.replan;

import com.aidevos.orchestrator.execution.ExecutionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FailureClassifierTest {

	private final FailureClassifier classifier = new FailureClassifier();

	@Test
	void shouldClassifyKnownFailureCategories() {
		assertEquals(FailureClassification.ARTIFACT_MISSING,
			classifier.classify("missing", null, true));
		assertEquals(FailureClassification.TRANSIENT,
			classifier.classify("request timeout", null, false));
		assertEquals(FailureClassification.AGENT_UNAVAILABLE,
			classifier.classify("agent unavailable", null, false));
		assertEquals(FailureClassification.VALIDATION_FAILED,
			classifier.classify("validation failed", null, false));
		assertEquals(FailureClassification.PLAN_INVALID,
			classifier.classify("invalid plan", null, false));
		assertEquals(FailureClassification.USER_REQUIRED_CHANGE,
			classifier.classify("user input required", null, false));
		assertEquals(FailureClassification.UNKNOWN,
			classifier.classify("unexpected", null, false));
	}

	@Test
	void toolMetadataShouldClassifyToolError() {
		ExecutionResult result = new ExecutionResult();
		result.getMetadata().put("toolResultCode", "MCP_ERROR");

		assertEquals(FailureClassification.TOOL_ERROR,
			classifier.classify("failed", result, false));
	}
}
