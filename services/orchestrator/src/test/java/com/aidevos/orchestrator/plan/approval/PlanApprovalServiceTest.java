package com.aidevos.orchestrator.plan.approval;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.approval.ApprovalStatus;
import com.aidevos.orchestrator.plan.AgentAssignment;
import com.aidevos.orchestrator.plan.ExpectedArtifact;
import com.aidevos.orchestrator.plan.FailurePolicy;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanSnapshot;
import com.aidevos.orchestrator.plan.PlanStep;
import com.aidevos.orchestrator.plan.PlanValidator;
import com.aidevos.orchestrator.plan.RetryPolicy;
import com.aidevos.orchestrator.plan.StepStatus;
import com.aidevos.orchestrator.planner.PlanDraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanApprovalServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-02T05:00:00Z");

	private PlanApprovalStore store;
	private PlanApprovalService service;

	@BeforeEach
	void setUp() {
		store = new PlanApprovalStore();
		service = new PlanApprovalService(store, new PlanValidator(), new ObjectMapper(),
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void shouldConvertDraftToPlanAndCreatePendingApproval() {
		Plan plan = draft(1, "Goal").toPlan();

		PlanApprovalRequest approval = service.create("request-1", plan);

		assertSame(plan, approval.getPlan());
		assertEquals("plan-1", approval.getPlanId());
		assertEquals(1, approval.getPlanVersion());
		assertEquals("request-1", approval.getRequestId());
		assertEquals(64, approval.getPlanSnapshotHash().length());
		assertEquals(NOW, approval.getCreatedAt());
		assertEquals(ApprovalStatus.PENDING, approval.getStatus());
		assertNull(approval.getDecision());
	}

	@Test
	void shouldApproveWithCompleteAuditFields() {
		PlanApprovalRequest approval = service.create("request-1", draft(1, "Goal").toPlan());

		service.approve(approval.getId(), "alice");

		assertEquals(ApprovalStatus.APPROVED, approval.getStatus());
		assertEquals(ApprovalStatus.APPROVED, approval.getDecision());
		assertEquals("alice", approval.getApprover());
		assertEquals(NOW, approval.getDecidedAt());
		assertNull(approval.getRejectionReason());
	}

	@Test
	void shouldRejectAndRetainReason() {
		PlanApprovalRequest approval = service.create("request-1", draft(1, "Goal").toPlan());

		service.reject(approval.getId(), "reviewer", "Plan needs a safer step");

		assertEquals(ApprovalStatus.REJECTED, approval.getStatus());
		assertEquals(ApprovalStatus.REJECTED, approval.getDecision());
		assertEquals("reviewer", approval.getApprover());
		assertEquals("Plan needs a safer step", approval.getRejectionReason());
		assertEquals(NOW, approval.getDecidedAt());
	}

	@Test
	void changedContentShouldRequireNewVersionAndNewApproval() {
		PlanApprovalRequest first = service.create("request-1", draft(1, "First goal").toPlan());
		service.approve(first.getId(), "alice");

		assertThrows(IllegalStateException.class,
			() -> service.create("request-1", draft(1, "Changed goal").toPlan()));
		PlanApprovalRequest second = service.create("request-1", draft(2, "Changed goal").toPlan());

		assertNotEquals(first.getId(), second.getId());
		assertNotEquals(first.getPlanSnapshotHash(), second.getPlanSnapshotHash());
		assertEquals(ApprovalStatus.PENDING, second.getStatus());
		assertEquals(2, second.getPlanVersion());
	}

	@Test
	void approvedPlanCannotBeModifiedOrRedecided() {
		Plan plan = draft(1, "Goal").toPlan();
		PlanApprovalRequest approval = service.create("request-1", plan);
		service.approve(approval.getId(), "alice");

		assertThrows(UnsupportedOperationException.class,
			() -> plan.steps().add(plan.steps().getFirst()));
		assertThrows(IllegalStateException.class,
			() -> service.reject(approval.getId(), "bob", "Changed mind"));
		assertSame(plan, approval.getPlan());
	}

	@Test
	void shouldPreserveApprovalDecisionWhenConsumed() {
		PlanApprovalRequest approval = service.create("request-1", draft(1, "Goal").toPlan());
		service.approve(approval.getId(), "alice");

		service.consume(approval.getId());

		assertEquals(ApprovalStatus.CONSUMED, approval.getStatus());
		assertEquals(ApprovalStatus.APPROVED, approval.getDecision());
		assertEquals("alice", approval.getApprover());
		assertNotNull(approval.getDecidedAt());
	}

	@Test
	void identicalPlanVersionShouldReuseExistingApproval() {
		Plan plan = draft(1, "Goal").toPlan();
		PlanApprovalRequest first = service.create("request-1", plan);

		PlanApprovalRequest duplicate = service.create("request-1", plan);

		assertSame(first, duplicate);
		assertEquals(1, service.getAll().size());
	}

	@Test
	void differentPlanningRequestShouldReceiveSeparatelyBoundApproval() {
		Plan plan = draft(1, "Goal").toPlan();
		PlanApprovalRequest first = service.create("request-1", plan);

		PlanApprovalRequest second = service.create("request-2", plan);

		assertNotEquals(first.getId(), second.getId());
		assertEquals("request-1", first.getRequestId());
		assertEquals("request-2", second.getRequestId());
		assertEquals(first.getPlanSnapshotHash(), second.getPlanSnapshotHash());
	}

	@Test
	void consumeIsAtomicAndIdempotent() {
		PlanApprovalRequest approval = service.create("request-1", draft(1, "Goal").toPlan());
		service.approve(approval.getId(), "alice");

		assertTrue(store.consumeIfApproved(approval.getId()));
		assertFalse(store.consumeIfApproved(approval.getId()));
		assertEquals(ApprovalStatus.CONSUMED, approval.getStatus());
		assertEquals(ApprovalStatus.CONSUMED, service.consume(approval.getId()).getStatus());
	}

	@Test
	void pendingOrMissingApprovalCannotBeConsumed() {
		PlanApprovalRequest pending = service.create("request-1", draft(1, "Goal").toPlan());

		assertFalse(store.consumeIfApproved(pending.getId()));
		assertFalse(store.consumeIfApproved("missing-approval"));
		assertThrows(IllegalStateException.class, () -> service.consume(pending.getId()));
	}

	private PlanDraft draft(int version, String goal) {
		PlanStep step = new PlanStep("step-1", "Analyze", "Analyze request",
			StepStatus.PLANNED, new AgentAssignment("coder", List.of("coding"), List.of()),
			null, null, Map.of(),
			List.of(new ExpectedArtifact("text", "analysis", "text/plain", true, 1)),
			new RetryPolicy(1, Duration.ZERO, List.of()), FailurePolicy.STOP_PLAN, false);
		return new PlanDraft("plan-1", version, goal, List.of(step), List.of(), snapshot(),
			"hermes", "none", "prompt-v1", Map.of("source", "test"));
	}

	private PlanSnapshot snapshot() {
		return new PlanSnapshot(List.of(new PlanSnapshot.AgentSnapshot("coder", "codex",
			List.of("coding"), "workspace-write", true)), Set.of("coding"), List.of(),
			Set.of("codex"), "policy-v1", Map.of("planner", "hermes"));
	}
}
