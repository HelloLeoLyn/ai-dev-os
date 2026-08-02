package com.aidevos.orchestrator.plan;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.tool.ToolAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanValidatorTest {

	private final PlanValidator validator = new PlanValidator();

	@Test
	void shouldAcceptValidFakePlan() {
		PlanValidationResult result = validator.validate(validPlan());

		assertTrue(result.valid(), () -> String.join(", ", result.errors()));
		assertTrue(result.errors().isEmpty());
	}

	@Test
	void shouldRejectCyclicDependency() {
		Plan valid = validPlan();
		Plan cyclic = copy(valid, valid.steps(), List.of(
			new Dependency("analyze", "read", true),
			new Dependency("read", "analyze", true)));

		assertError(cyclic, "PLAN_DEPENDENCY_CYCLE");
	}

	@Test
	void shouldRejectUnknownAgent() {
		Plan valid = validPlan();
		PlanStep invalid = step("analyze", new AgentAssignment("missing", List.of(), List.of()),
			null, null, Map.of(), RetryPolicy.noRetry());

		assertError(copy(valid, List.of(invalid), List.of()), "UNKNOWN_AGENT:missing");
	}

	@Test
	void shouldRejectUnknownTool() {
		Plan valid = validPlan();
		PlanStep invalid = step("read", new AgentAssignment("tool-agent", List.of("tool"), List.of()),
			"filesystem", "missing", Map.of("path", "README.md"), RetryPolicy.noRetry());

		assertError(copy(valid, List.of(invalid), List.of()), "UNKNOWN_TOOL:filesystem/missing");
	}

	@Test
	void shouldRejectRetryAboveLimit() {
		Plan valid = validPlan();
		PlanStep invalid = step("analyze", new AgentAssignment("coder", List.of("coding"), List.of()),
			null, null, Map.of(), new RetryPolicy(4, Duration.ZERO, List.of("TIMEOUT")));

		assertError(copy(valid, List.of(invalid), List.of()), "RETRY_LIMIT_INVALID:analyze");
	}

	@Test
	void shouldRejectUnknownExecutorAndApprovalBypass() {
		Plan valid = validPlan();
		PlanSnapshot invalidSnapshot = new PlanSnapshot(List.of(
			new PlanSnapshot.AgentSnapshot("coder", "missing-executor", List.of("coding"),
				"standard", true)), Set.of("coding"), valid.snapshot().tools(), Set.of("codex"),
			"policy-v1", Map.of());
		PlanStep bypass = new PlanStep("analyze", "Analyze", "Analyze request", StepStatus.PLANNED,
			new AgentAssignment("coder", List.of("coding"), List.of()), null, null, Map.of(),
			List.of(), RetryPolicy.noRetry(), FailurePolicy.STOP_PLAN, true);
		Plan invalid = new Plan("plan-1", 1, "Goal", PlanStatus.DRAFT, List.of(bypass),
			List.of(), invalidSnapshot, Instant.now());

		PlanValidationResult result = validator.validate(invalid);

		assertTrue(result.errors().contains("UNKNOWN_EXECUTOR:missing-executor"));
		assertTrue(result.errors().contains("APPROVAL_BYPASS_FORBIDDEN:analyze"));
	}

	@Test
	void shouldDeeplyFreezeSnapshotAndStepArguments() {
		Map<String, Object> nested = new java.util.LinkedHashMap<>();
		nested.put("values", new java.util.ArrayList<>(List.of("a")));
		PlanStep step = step("read", new AgentAssignment("tool-agent", List.of("tool"), List.of()),
			"filesystem", "read_text_file", nested, RetryPolicy.noRetry());
		PlanSnapshot snapshot = new PlanSnapshot(List.of(), Set.of(), List.of(), Set.of(),
			"policy-v1", Map.of("planner", nested));

		assertThrows(UnsupportedOperationException.class,
			() -> ((List<Object>) step.toolArguments().get("values")).add("b"));
		assertThrows(UnsupportedOperationException.class,
			() -> ((Map<String, Object>) snapshot.plannerMetadata().get("planner")).put("x", "y"));
	}

	private Plan validPlan() {
		PlanStep analyze = step("analyze",
			new AgentAssignment("coder", List.of("coding"), List.of()),
			null, null, Map.of(), RetryPolicy.noRetry());
		PlanStep read = step("read",
			new AgentAssignment("tool-agent", List.of("tool"), List.of()),
			"filesystem", "read_text_file", Map.of("path", "README.md"),
			new RetryPolicy(2, Duration.ofMillis(10), List.of("TOOL_TIMEOUT")));
		return new Plan("plan-1", 1, "Analyze the project", PlanStatus.DRAFT,
			List.of(analyze, read), List.of(new Dependency("analyze", "read", true)),
			snapshot(), Instant.now());
	}

	private PlanSnapshot snapshot() {
		return new PlanSnapshot(List.of(
			new PlanSnapshot.AgentSnapshot("coder", "codex", List.of("coding", "git"),
				"standard", true),
			new PlanSnapshot.AgentSnapshot("tool-agent", "tool", List.of("tool", "read-only"),
				"read-only", true)),
			Set.of("coding", "git", "tool", "read-only"),
			List.of(new PlanSnapshot.ToolSnapshot("filesystem", "read_text_file",
				ToolAccess.READ_ONLY)), Set.of("codex", "tool"), "policy-v1",
			Map.of("planner", "fake", "model", "none"));
	}

	private PlanStep step(String id, AgentAssignment assignment, String provider, String tool,
			Map<String, Object> arguments, RetryPolicy retry) {
		return new PlanStep(id, id, "Fake step " + id, StepStatus.PLANNED, assignment,
			provider, tool, arguments,
			List.of(new ExpectedArtifact("text", id + ".txt", "text/plain", true, 1)),
			retry, FailurePolicy.STOP_PLAN, false);
	}

	private Plan copy(Plan source, List<PlanStep> steps, List<Dependency> dependencies) {
		return new Plan(source.id(), source.version(), source.goal(), source.status(), steps,
			dependencies, source.snapshot(), source.createdAt());
	}

	private void assertError(Plan plan, String expected) {
		PlanValidationResult result = validator.validate(plan);
		assertFalse(result.valid());
		assertTrue(result.errors().contains(expected), () -> String.join(", ", result.errors()));
	}
}
