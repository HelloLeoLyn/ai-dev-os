package com.aidevos.orchestrator.planner;

import java.util.Map;

import com.aidevos.orchestrator.execution.ValidationProfile;
import com.aidevos.orchestrator.execution.tool.DeterministicTool;
import com.aidevos.orchestrator.plan.StepExecutionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepClassifierTest {

	@Test
	void modifyJavaCodeIsClassifiedAsAiStep() {
		assertEquals(StepExecutionType.AI_STEP,
			StepClassifier.classify(StepExecutionType.AI_STEP, null, null));
	}

	@Test
	void runMavenCompileIsClassifiedAsToolStepWithMavenTool() {
		assertEquals(StepExecutionType.TOOL_STEP,
			StepClassifier.classify(StepExecutionType.AI_STEP, "deterministic", "maven"));
		assertEquals(DeterministicTool.MAVEN, DeterministicTool.fromName("maven").orElseThrow());
	}

	@Test
	void runTargetedTestIsClassifiedAsToolStepWithMavenTool() {
		assertEquals(StepExecutionType.TOOL_STEP,
			StepClassifier.classify(null, "deterministic", "mvn"));
		assertEquals(DeterministicTool.MAVEN, DeterministicTool.fromName("mvn").orElseThrow());
	}

	@Test
	void waitForHumanApprovalStaysHumanGate() {
		assertEquals(StepExecutionType.HUMAN_GATE,
			StepClassifier.classify(StepExecutionType.HUMAN_GATE, null, null));
	}

	@Test
	void legacyPlanWithoutExecutionTypeDefaultsToAiStep() {
		assertEquals(StepExecutionType.AI_STEP,
			StepClassifier.classify(null, null, null));
		assertEquals(StepExecutionType.AI_STEP,
			StepClassifier.classify(null, "filesystem", "read_text_file"));
	}

	@Test
	void unknownToolIsDowngradedFromToolStepToAiStep() {
		assertEquals(StepExecutionType.AI_STEP,
			StepClassifier.classify(StepExecutionType.TOOL_STEP, "deterministic", "custom-tool"));
		assertEquals(StepExecutionType.AI_STEP,
			StepClassifier.classify(StepExecutionType.TOOL_STEP, "filesystem", "read_text_file"));
	}

	@Test
	void validationProfileDefaultsToFastAndHonorsExplicitUserRequest() {
		assertEquals(ValidationProfile.FAST, StepClassifier.validationProfile(Map.of()));
		assertEquals(ValidationProfile.TARGETED,
			StepClassifier.validationProfile(Map.of("validationProfile", "TARGETED")));
		assertEquals(ValidationProfile.FULL,
			StepClassifier.validationProfile(Map.of("validationProfile", "FULL")));
		assertEquals(ValidationProfile.FAST,
			StepClassifier.validationProfile(Map.of("validationProfile", "BOGUS")));
		assertTrue(ValidationProfile.FAST.ordinal() < ValidationProfile.FULL.ordinal());
	}
}
