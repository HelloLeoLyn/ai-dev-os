package com.aidevos.orchestrator.plan.schedule;

import com.aidevos.orchestrator.approval.ApprovalStatus;
import com.aidevos.orchestrator.audit.*;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.job.*;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.plan.*;
import com.aidevos.orchestrator.plan.approval.*;
import com.aidevos.orchestrator.plan.run.*;
import com.aidevos.orchestrator.planner.replan.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlanSchedulerAuditTest {
	private PlanScheduler scheduler;
	@AfterEach void stop() { if (scheduler != null) scheduler.stopMonitor(); }

	@Test
	void recordsRealTransitionsButNotRepeatedReconcile() {
		Instant now = Instant.parse("2026-08-03T00:00:00Z");
		InMemoryAuditRepository events = new InMemoryAuditRepository();
		AuditService audit = new AuditService(events);
		JobService jobs = mock(JobService.class);
		PlanApprovalService approvals = mock(PlanApprovalService.class);
		ReplanRequestService replans = mock(ReplanRequestService.class);
		Plan plan = new Plan("plan-1", 1, "goal", PlanStatus.APPROVED,
			List.of(new PlanStep("step-1", "step", "execute", StepStatus.PLANNED,
				new AgentAssignment("agent", List.of(), List.of()), null, null, Map.of(), List.of(),
				RetryPolicy.noRetry(), FailurePolicy.STOP_PLAN, false)), List.of(), null, now);
		PlanApprovalRequest approval = mock(PlanApprovalRequest.class);
		when(approval.getStatus()).thenReturn(ApprovalStatus.APPROVED);
		when(approval.getPlan()).thenReturn(plan);
		when(approvals.get("approval-1")).thenReturn(approval);
		PlanApprovalRequest consumed = mock(PlanApprovalRequest.class);
		when(consumed.getStatus()).thenReturn(ApprovalStatus.CONSUMED);
		when(approvals.consume("approval-1")).thenReturn(consumed);
		when(jobs.submit(any(TaskDefinition.class))).thenReturn(
			new JobSubmissionResponse("job-1", "task-1", JobStatus.QUEUED));
		TaskDefinition task = new TaskDefinition(); task.setId("task-1");
		ExecutionJob job = new ExecutionJob("job-1", task); job.markRunning();
		when(jobs.get("job-1")).thenReturn(job);
		scheduler = new PlanScheduler(jobs, new StepTaskFactory(), approvals, replans,
			new InMemoryPlanRunRepository(), Clock.fixed(now, ZoneOffset.UTC), audit);

		PlanRun run = scheduler.start("approval-1");
		scheduler.reconcile();
		scheduler.reconcile();

		assertEquals(PlanRunStatus.RUNNING, run.getStatus());
		assertEquals(List.of(EventType.PLAN_RUN_CREATED, EventType.PLAN_RUN_STARTED),
			events.query(EventQuery.all()).stream().map(EventRecord::type).toList());
	}
}
