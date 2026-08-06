package com.aidevos.orchestrator.taskcenter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.approval.ApprovalStatus;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.PlanRunRepository;
import com.aidevos.orchestrator.planner.PlanningResult;
import com.aidevos.orchestrator.planner.PlannerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskCenterServiceTest {

	private PlannerService plannerService;
	private PlanApprovalService approvalService;
	private PlanRunRepository planRunRepository;
	private TaskCenterService service;

	private static final Plan PLAN = new Plan("plan-1", 1, "goal", PlanStatus.DRAFT,
		List.of(), List.of(), null, Instant.parse("2026-08-01T00:00:00Z"));

	@BeforeEach
	void setUp() {
		plannerService = mock(PlannerService.class);
		approvalService = mock(PlanApprovalService.class);
		planRunRepository = mock(PlanRunRepository.class);
		service = new TaskCenterService(plannerService, approvalService, planRunRepository);
	}

	@Test
	void shouldCreateTaskAndMoveToPlanning() {
		when(plannerService.createPlan(any()))
			.thenReturn(PlanningResult.success("hermes", null, PLAN));
		when(approvalService.create(any(), any())).thenReturn(
			new PlanApprovalRequest("approval-1", "task-1", PLAN, "hash",
				Instant.parse("2026-08-01T00:00:00Z")));

		TaskRecord task = service.createTask(new CreateTaskRequest(
			"Implement login", "Login flow", "Implement a login flow", "hermes"));

		assertEquals(TaskStatus.PLANNING, task.getStatus());
		assertEquals("approval-1", task.getApprovalId());
		assertTrue(task.getTaskId().startsWith("task-"));
	}

	@Test
	void shouldMarkTaskFailedWhenPlanningFails() {
		when(plannerService.createPlan(any()))
			.thenReturn(PlanningResult.failure("hermes", null, List.of("PLANNER_FAILED")));

		TaskRecord task = service.createTask(new CreateTaskRequest(
			"Broken", null, "Goal", null));

		assertEquals(TaskStatus.FAILED, task.getStatus());
		assertEquals("PLANNER_FAILED", task.getErrorMessage());
	}

	@Test
	void shouldRefreshToApprovedWhenApprovalApproved() {
		when(plannerService.createPlan(any()))
			.thenReturn(PlanningResult.success("hermes", null, PLAN));
		PlanApprovalRequest approval = new PlanApprovalRequest("approval-1", "task-1", PLAN,
			"hash", Instant.parse("2026-08-01T00:00:00Z"));
		approval.approve("user-1", Instant.parse("2026-08-01T00:05:00Z"));
		when(approvalService.create(any(), any())).thenReturn(approval);
		when(approvalService.get("approval-1")).thenReturn(approval);
		when(planRunRepository.findRunIdByApproval("approval-1")).thenReturn(null);

		TaskRecord task = service.createTask(new CreateTaskRequest(
			"Implement login", null, "Goal", null));
		Optional<TaskRecord> refreshed = service.getTask(task.getTaskId());

		assertTrue(refreshed.isPresent());
		assertEquals(TaskStatus.APPROVED, refreshed.get().getStatus());
	}

	@Test
	void shouldRefreshToSuccessWhenPlanRunSucceeds() {
		when(plannerService.createPlan(any()))
			.thenReturn(PlanningResult.success("hermes", null, PLAN));
		PlanApprovalRequest approval = new PlanApprovalRequest("approval-1", "task-1", PLAN,
			"hash", Instant.parse("2026-08-01T00:00:00Z"));
		approval.approve("user-1", Instant.parse("2026-08-01T00:05:00Z"));
		when(approvalService.create(any(), any())).thenReturn(approval);
		when(approvalService.get("approval-1")).thenReturn(approval);
		PlanRun run = new PlanRun("run-1", "approval-1", PLAN, List.of(),
			Instant.parse("2026-08-01T00:06:00Z"));
		run.markSuccess(Instant.parse("2026-08-01T00:10:00Z"));
		when(planRunRepository.findRunIdByApproval("approval-1")).thenReturn("run-1");
		when(planRunRepository.get("run-1")).thenReturn(run);

		TaskRecord task = service.createTask(new CreateTaskRequest(
			"Implement login", null, "Goal", null));
		Optional<TaskRecord> refreshed = service.getTask(task.getTaskId());

		assertTrue(refreshed.isPresent());
		assertEquals(TaskStatus.SUCCESS, refreshed.get().getStatus());
		assertEquals("run-1", refreshed.get().getPlanRunId());
	}

	@Test
	void shouldRefreshToFailedWhenPlanRunFails() {
		when(plannerService.createPlan(any()))
			.thenReturn(PlanningResult.success("hermes", null, PLAN));
		PlanApprovalRequest approval = new PlanApprovalRequest("approval-1", "task-1", PLAN,
			"hash", Instant.parse("2026-08-01T00:00:00Z"));
		approval.approve("user-1", Instant.parse("2026-08-01T00:05:00Z"));
		when(approvalService.create(any(), any())).thenReturn(approval);
		when(approvalService.get("approval-1")).thenReturn(approval);
		PlanRun run = new PlanRun("run-1", "approval-1", PLAN, List.of(),
			Instant.parse("2026-08-01T00:06:00Z"));
		run.markFailed("step failed", Instant.parse("2026-08-01T00:10:00Z"));
		when(planRunRepository.findRunIdByApproval("approval-1")).thenReturn("run-1");
		when(planRunRepository.get("run-1")).thenReturn(run);

		TaskRecord task = service.createTask(new CreateTaskRequest(
			"Implement login", null, "Goal", null));
		Optional<TaskRecord> refreshed = service.getTask(task.getTaskId());

		assertTrue(refreshed.isPresent());
		assertEquals(TaskStatus.FAILED, refreshed.get().getStatus());
		assertEquals("step failed", refreshed.get().getErrorMessage());
	}

	@Test
	void shouldReturnEmptyForUnknownTask() {
		assertTrue(service.getTask("missing").isEmpty());
		assertEquals(0, service.listTasks().size());
	}
}
