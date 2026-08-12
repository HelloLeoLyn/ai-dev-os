package com.aidevos.orchestrator.taskcenter;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.PlanRunRepository;
import com.aidevos.orchestrator.plan.schedule.PlanScheduler;
import com.aidevos.orchestrator.planner.PlannerService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskCenterApprovalTest {

	@Test
	void approveStartsOneRunAndIsIdempotent() {
		Fixture fixture = fixture("task-1");
		when(fixture.runs.findRunIdByApproval("approval-1")).thenReturn(null, "run-1", "run-1");
		PlanRun run = new PlanRun("run-1", "approval-1", "task-1", fixture.plan,
			List.of(), Instant.now());
		when(fixture.scheduler.start("approval-1")).thenReturn(run);

		TaskRecord first = fixture.service.approve("task-1", "alice");
		TaskRecord second = fixture.service.approve("task-1", "alice");

		assertSame(first, second);
		assertEquals(TaskStatus.RUNNING, second.getStatus());
		assertEquals("run-1", second.getPlanRunId());
		verify(fixture.approvals, times(1)).approve("approval-1", "alice");
		verify(fixture.scheduler, times(1)).start("approval-1");
	}

	@Test
	void rejectIsIdempotentAndNeverStartsRun() {
		Fixture fixture = fixture("task-1");
		when(fixture.approvals.reject("approval-1", "alice", "unsafe")).thenAnswer(call -> {
			fixture.approval.reject("alice", "unsafe", Instant.now());
			return fixture.approval;
		});

		fixture.service.reject("task-1", "alice", "unsafe");
		TaskRecord repeated = fixture.service.reject("task-1", "alice", "unsafe");

		assertEquals(TaskStatus.REJECTED, repeated.getStatus());
		assertEquals("unsafe", repeated.getErrorMessage());
		assertNull(repeated.getPlanRunId());
		verify(fixture.approvals, times(1)).reject("approval-1", "alice", "unsafe");
		verifyNoInteractions(fixture.scheduler);
	}

	@Test
	void rejectsApprovalOwnedByAnotherTask() {
		Fixture fixture = fixture("another-task");
		IllegalStateException failure = assertThrows(IllegalStateException.class,
			() -> fixture.service.approve("task-1", "alice"));
		assertTrue(failure.getMessage().contains("requestId"));
		verifyNoInteractions(fixture.scheduler);
	}

	private Fixture fixture(String approvalRequestId) {
		PlannerService planner = mock(PlannerService.class);
		PlanApprovalService approvals = mock(PlanApprovalService.class);
		PlanRunRepository runs = mock(PlanRunRepository.class);
		PlanScheduler scheduler = mock(PlanScheduler.class);
		InMemoryTaskRepository tasks = new InMemoryTaskRepository();
		TaskRecord task = new TaskRecord("task-1", "Task", "Description", "project-1",
			"workspace-1", ExecutionMode.READ_ONLY);
		task.markPlanning("approval-1");
		tasks.save(task);
		Plan plan = new Plan("plan-1", 1, "Goal", PlanStatus.DRAFT, List.of(), List.of(),
			null, Instant.now());
		PlanApprovalRequest approval = new PlanApprovalRequest("approval-1", approvalRequestId,
			plan, "hash", Instant.now());
		when(approvals.get("approval-1")).thenReturn(approval);
		when(approvals.approve("approval-1", "alice")).thenAnswer(call -> {
			approval.approve("alice", Instant.now());
			return approval;
		});
		TaskCenterService service = new TaskCenterService(planner, approvals, runs, null,
			AuditService.noop(), tasks, scheduler);
		return new Fixture(service, approvals, runs, scheduler, approval, plan);
	}

	private record Fixture(TaskCenterService service, PlanApprovalService approvals,
			PlanRunRepository runs, PlanScheduler scheduler, PlanApprovalRequest approval,
			Plan plan) { }
}
