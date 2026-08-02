package com.aidevos.orchestrator.planner.replan;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.plan.AgentAssignment;
import com.aidevos.orchestrator.plan.ExpectedArtifact;
import com.aidevos.orchestrator.plan.FailurePolicy;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanSnapshot;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.PlanStep;
import com.aidevos.orchestrator.plan.PlanValidator;
import com.aidevos.orchestrator.plan.RetryPolicy;
import com.aidevos.orchestrator.plan.StepStatus;
import com.aidevos.orchestrator.planner.HermesPlanner;
import com.aidevos.orchestrator.planner.PlannerService;
import com.aidevos.orchestrator.tool.ToolAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplanningTest {

	@Test
	void hermesShouldGenerateValidatedNextVersionWithoutChangingOriginal() {
		Plan original = plan(1, List.of(step("completed", "coder", null, null)), snapshot(false));
		ReplanRequest request = request(original, List.of("completed"));
		PlanValidator planValidator = new PlanValidator();
		PlannerService service = new PlannerService(List.of(new HermesPlanner()), planValidator,
			new ReplanValidator(planValidator));

		ReplanningResult result = service.replan(HermesPlanner.NAME, request);

		assertTrue(result.success(), () -> String.join(",", result.errors()));
		assertEquals(2, result.plan().version());
		assertEquals(1, original.version());
		assertNotSame(original, result.plan());
		assertEquals(PlanStatus.DRAFT, result.plan().status());
		assertTrue(result.newApprovalRequired());
		assertTrue(result.approvalReasons().contains("ORIGINAL_APPROVAL_NOT_REUSABLE"));
	}

	@Test
	void newToolAndPermissionShouldRequireExplicitNewApproval() {
		Plan original = plan(1, List.of(step("completed", "coder", null, null)), snapshot(false));
		Plan candidate = plan(2, List.of(
			step("completed", "coder", null, null),
			step("write", "writer", "filesystem", "write_file")), snapshot(true));

		ReplanValidationResult result = new ReplanValidator(new PlanValidator())
			.validate(request(original, List.of("completed")), candidate);

		assertTrue(result.valid(), () -> String.join(",", result.errors()));
		assertTrue(result.newApprovalRequired());
		assertTrue(result.approvalReasons().contains("NEW_TOOL_REQUIRES_APPROVAL"));
		assertTrue(result.approvalReasons().contains("NEW_PERMISSION_REQUIRES_APPROVAL"));
		assertTrue(result.approvalReasons().contains("ORIGINAL_APPROVAL_NOT_REUSABLE"));
	}

	@Test
	void completedStepCannotBeDroppedAndVersionMustIncrement() {
		Plan original = plan(1, List.of(step("completed", "coder", null, null)), snapshot(false));
		Plan invalid = plan(1, List.of(step("replacement", "coder", null, null)), snapshot(false));

		ReplanValidationResult result = new ReplanValidator(new PlanValidator())
			.validate(request(original, List.of("completed")), invalid);

		assertFalse(result.valid());
		assertTrue(result.errors().contains("REPLAN_VERSION_MUST_INCREMENT"));
		assertTrue(result.errors().contains("COMPLETED_STEP_MISSING:completed"));
	}

	private ReplanRequest request(Plan original, List<String> completed) {
		return new ReplanRequest("replan-1", original.id(), original.version(), "run-1",
			"failed", FailureClassification.TOOL_ERROR, "tool failed", completed, null,
			List.of(), original, Instant.now());
	}

	private Plan plan(int version, List<PlanStep> steps, PlanSnapshot snapshot) {
		return new Plan("plan-1", version, "Goal", PlanStatus.DRAFT, steps, List.of(), snapshot,
			Instant.now());
	}

	private PlanStep step(String id, String agent, String provider, String tool) {
		return new PlanStep(id, id, "Step " + id, StepStatus.PLANNED,
			new AgentAssignment(agent, List.of(), List.of()), provider, tool,
			tool == null ? Map.of() : Map.of("path", "file.txt"),
			List.of(new ExpectedArtifact("text", null, null, false, 0)),
			new RetryPolicy(1, Duration.ZERO, List.of()), FailurePolicy.STOP_PLAN, false);
	}

	private PlanSnapshot snapshot(boolean risky) {
		List<PlanSnapshot.AgentSnapshot> agents = risky
			? List.of(
				new PlanSnapshot.AgentSnapshot("coder", "codex", List.of("coding"),
					"read-only", true),
				new PlanSnapshot.AgentSnapshot("writer", "tool", List.of("tool"),
					"workspace-write", true))
			: List.of(new PlanSnapshot.AgentSnapshot("coder", "codex", List.of("coding"),
				"read-only", true));
		return new PlanSnapshot(agents, risky ? Set.of("coding", "tool") : Set.of("coding"),
			risky ? List.of(new PlanSnapshot.ToolSnapshot("filesystem", "write_file",
				ToolAccess.WORKSPACE_WRITE)) : List.of(),
			risky ? Set.of("codex", "tool") : Set.of("codex"), "policy-v1",
			Map.of("planner", "hermes", "model", "none", "promptVersion", "prompt-v1"));
	}
}
