package com.aidevos.orchestrator.plan;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutablePlanGuardTest {

	@Test
	void rejectsMockAndUnsupportedArtifactForReadOnlyProjectAnalysis() {
		PlanSnapshot snapshot = new PlanSnapshot(List.of(
			new PlanSnapshot.AgentSnapshot("planner", "mock", List.of("analysis"), null, true)),
			Set.of("analysis"), List.of(), Set.of("mock"), "v1",
			Map.of("taskType", "project-analysis", "executionMode", "READ_ONLY"));
		PlanStep step = new PlanStep("analysis", "Analysis", "Analyze", StepStatus.PLANNED,
			new AgentAssignment("planner", List.of("analysis"), List.of()), Map.of(), List.of(),
			null, null, Map.of(),
			List.of(new ExpectedArtifact("result", "result", "application/json", true, 1)),
			RetryPolicy.noRetry(), FailurePolicy.STOP_PLAN, false);
		Plan plan = new Plan("plan", 1, "Analyze", PlanStatus.DRAFT, List.of(step), List.of(),
			snapshot, Instant.now());

		List<String> errors = ExecutablePlanGuard.errors(plan);

		assertTrue(errors.contains("PROJECT_ANALYSIS_MOCK_EXECUTOR_FORBIDDEN:analysis"));
		assertTrue(errors.contains("READ_ONLY_AGENT_REQUIRED:analysis"));
	}

	@Test
	void acceptsCodexReadOnlyContract() {
		PlanSnapshot snapshot = new PlanSnapshot(List.of(
			new PlanSnapshot.AgentSnapshot("analyst", "codex",
				List.of("analysis", "read-only"), "read-only", true)),
			Set.of("analysis", "read-only"), List.of(), Set.of("codex"), "v1",
			Map.of("taskType", "project-analysis", "executionMode", "READ_ONLY"));
		PlanStep step = new PlanStep("analysis", "Analysis", "Analyze", StepStatus.PLANNED,
			new AgentAssignment("analyst", List.of("analysis", "read-only"), List.of()),
			Map.of("sandbox", "read-only"), List.of(), null, null, Map.of(),
			List.of(new ExpectedArtifact("codex-result", "codex-result.txt", "text/plain",
				true, 1)), RetryPolicy.noRetry(), FailurePolicy.STOP_PLAN, false);
		Plan plan = new Plan("plan", 1, "Analyze", PlanStatus.DRAFT, List.of(step), List.of(),
			snapshot, Instant.now());

		assertTrue(ExecutablePlanGuard.errors(plan).isEmpty());
	}
}
