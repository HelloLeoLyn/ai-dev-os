package com.aidevos.orchestrator.plan.approval;

import com.aidevos.orchestrator.audit.*;
import com.aidevos.orchestrator.plan.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class PlanApprovalAuditTest {
	@Test
	void recordsApprovalLifecycle() {
		InMemoryAuditRepository events = new InMemoryAuditRepository();
		AuditService audit = new AuditService(events);
		PlanApprovalService service = new PlanApprovalService(new PlanApprovalStore(),
			new PlanValidator(), new ObjectMapper(), Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"),
				ZoneOffset.UTC), audit);
		PlanStep step = new PlanStep("step-1", "step", "execute", StepStatus.PLANNED,
			new AgentAssignment("agent", List.of(), List.of()), null, null, Map.of(), List.of(),
			RetryPolicy.noRetry(), FailurePolicy.STOP_PLAN, false);
		PlanSnapshot snapshot = new PlanSnapshot(List.of(new PlanSnapshot.AgentSnapshot("agent",
			"mock", List.of(), "read-only", true)), Set.of(), List.of(), Set.of("mock"),
			"policy-v1", Map.of());
		Plan plan = new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(step), List.of(),
			snapshot, Instant.parse("2026-08-03T00:00:00Z"));

		PlanApprovalRequest approval = service.create("request-1", plan);
		service.approve(approval.getId(), "reviewer");
		service.consume(approval.getId());

		assertEquals(List.of(EventType.PLAN_APPROVAL_REQUESTED, EventType.PLAN_APPROVAL_APPROVED,
			EventType.PLAN_APPROVAL_CONSUMED), events.query(EventQuery.all()).stream()
			.map(EventRecord::type).toList());
	}
}
