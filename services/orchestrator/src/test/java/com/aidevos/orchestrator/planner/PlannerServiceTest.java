package com.aidevos.orchestrator.planner;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.plan.AgentAssignment;
import com.aidevos.orchestrator.plan.ExpectedArtifact;
import com.aidevos.orchestrator.plan.FailurePolicy;
import com.aidevos.orchestrator.plan.PlanSnapshot;
import com.aidevos.orchestrator.plan.PlanStep;
import com.aidevos.orchestrator.plan.PlanValidator;
import com.aidevos.orchestrator.plan.RetryPolicy;
import com.aidevos.orchestrator.plan.StepStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerServiceTest {

	@Test
	void fakePlannerShouldGeneratePlanThroughService() {
		PlanDraft draft = validDraft("fake", snapshot());
		PlannerService service = new PlannerService(List.of(new FakePlanner("fake", draft)),
			new PlanValidator());

		PlanningResult result = service.createPlan(request("fake", draft.snapshot()));

		assertTrue(result.success());
		assertSame(draft, result.draft());
		assertNotNull(result.plan());
		assertEquals("plan-1", result.plan().id());
	}

	@Test
	void hermesPlannerShouldGenerateFixedCandidateWithoutModelCall() {
		HermesPlanner planner = new HermesPlanner();
		PlanningRequest request = request(HermesPlanner.NAME, snapshot());

		PlanDraft draft = planner.plan(request);

		assertEquals(HermesPlanner.NAME, draft.plannerName());
		assertEquals("model-placeholder", draft.model());
		assertEquals("prompt-v1", draft.promptVersion());
		assertEquals("coder", draft.steps().getFirst().assignment().agentName());
	}

	@Test
	void serviceShouldRejectInvalidDraftUsingPlanValidator() {
		PlanSnapshot snapshot = snapshot();
		PlanStep invalid = new PlanStep("invalid", "Invalid", "Unknown agent",
			StepStatus.PLANNED, new AgentAssignment("missing", List.of(), List.of()),
			null, null, Map.of(), List.of(), RetryPolicy.noRetry(),
			FailurePolicy.STOP_PLAN, false);
		PlanDraft draft = new PlanDraft("invalid-plan", 1, "Invalid", List.of(invalid),
			List.of(), snapshot, "fake", null, "prompt-v1", Map.of());
		PlannerService service = new PlannerService(List.of(new FakePlanner("fake", draft)),
			new PlanValidator());

		PlanningResult result = service.createPlan(request("fake", snapshot));

		assertFalse(result.success());
		assertTrue(result.errors().contains("UNKNOWN_AGENT:missing"));
		assertEquals(null, result.plan());
	}

	@Test
	void planningRequestSnapshotShouldBeRetainedInDraftAndPlan() {
		PlanSnapshot snapshot = snapshot();
		PlanDraft draft = validDraft("fake", snapshot);
		PlannerService service = new PlannerService(List.of(new FakePlanner("fake", draft)),
			new PlanValidator());

		PlanningResult result = service.createPlan(request("fake", snapshot));

		assertTrue(result.success());
		assertSame(snapshot, result.draft().snapshot());
		assertSame(snapshot, result.plan().snapshot());
		assertEquals(Set.of("coding"), result.plan().snapshot().capabilities());
		assertEquals("policy-v1", result.plan().snapshot().policyVersion());
	}

	@Test
	void serviceShouldStandardizePlannerFailure() {
		Planner planner = FakePlanner.failing("broken", new IllegalStateException("secret"));
		PlannerService service = new PlannerService(List.of(planner), new PlanValidator());

		PlanningResult result = service.createPlan(request("broken", snapshot()));

		assertFalse(result.success());
		assertEquals(List.of("PLANNER_FAILED:IllegalStateException"), result.errors());
		assertEquals(null, result.draft());
		assertEquals(null, result.plan());
	}

	private PlanningRequest request(String plannerName, PlanSnapshot snapshot) {
		return new PlanningRequest("request-1", "Build a candidate plan", plannerName,
			"model-placeholder", "prompt-v1", Map.of("mode", "structured"), snapshot,
			Map.of("source", "test"));
	}

	private PlanDraft validDraft(String plannerName, PlanSnapshot snapshot) {
		PlanStep step = new PlanStep("step-1", "Analyze", "Analyze request",
			StepStatus.PLANNED, new AgentAssignment("coder", List.of("coding"), List.of()),
			null, null, Map.of(),
			List.of(new ExpectedArtifact("text", "analysis", "text/plain", true, 1)),
			new RetryPolicy(1, Duration.ZERO, List.of()), FailurePolicy.STOP_PLAN, false);
		return new PlanDraft("plan-1", 1, "Build a candidate plan", List.of(step), List.of(),
			snapshot, plannerName, "model-placeholder", "prompt-v1", Map.of("source", "test"));
	}

	private PlanSnapshot snapshot() {
		return new PlanSnapshot(List.of(new PlanSnapshot.AgentSnapshot("coder", "codex",
			List.of("coding"), "workspace-write", true)), Set.of("coding"), List.of(),
			Set.of("codex"), "policy-v1",
			Map.of("planner", "fake", "model", "model-placeholder", "promptVersion", "prompt-v1"));
	}
}
