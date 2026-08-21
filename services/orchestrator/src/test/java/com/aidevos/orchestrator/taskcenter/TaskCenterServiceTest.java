package com.aidevos.orchestrator.taskcenter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.agentcoordinator.AgentCoordinatorService;
import com.aidevos.orchestrator.approval.ApprovalStatus;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.change.ExecutionCompletionHandoffService;
import com.aidevos.orchestrator.modelrouter.TaskType;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.PlanRunRepository;
import com.aidevos.orchestrator.plan.run.PlanRunStatus;
import com.aidevos.orchestrator.planner.PlanningResult;
import com.aidevos.orchestrator.planner.PlannerService;
import com.aidevos.orchestrator.planner.PlanningRequest;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
			"Implement login", "Login flow", "Implement a login flow", "hermes", "default"));

		assertEquals(TaskStatus.PLANNING, task.getStatus());
		assertEquals("approval-1", task.getApprovalId());
		assertTrue(task.getTaskId().startsWith("task-"));
	}

	@Test
	void shouldMarkTaskFailedWhenPlanningFails() {
		when(plannerService.createPlan(any()))
			.thenReturn(PlanningResult.failure("hermes", null, List.of("PLANNER_FAILED")));

		TaskRecord task = service.createTask(new CreateTaskRequest(
			"Broken", null, "Goal", null, "default"));

		assertEquals(TaskStatus.FAILED, task.getStatus());
		assertEquals("PLANNER_FAILED", task.getErrorMessage());
	}

	@Test
	void shouldPersistTasksAcrossServiceInstancesAndCarryPlanningContext() {
		InMemoryTaskRepository repository = new InMemoryTaskRepository();
		service = new TaskCenterService(plannerService, approvalService, planRunRepository,
			null, AuditService.noop(), repository);
		when(plannerService.createPlan(any()))
			.thenReturn(PlanningResult.failure("hermes", null, List.of("PLANNED_READ_ONLY")));

		TaskRecord created = service.createTask(new CreateTaskRequest("Analyze", "Inspect",
			"Analyze project", "hermes", "project-1", "workspace-1",
			ExecutionMode.READ_ONLY), "/repo/jjx");
		TaskCenterService restarted = new TaskCenterService(plannerService, approvalService,
			planRunRepository, null, AuditService.noop(), repository);

		TaskRecord reloaded = restarted.getTask(created.getTaskId()).orElseThrow();
		assertEquals("project-1", reloaded.getProjectId());
		assertEquals("workspace-1", reloaded.getWorkspaceId());
		assertEquals(ExecutionMode.READ_ONLY, reloaded.getExecutionMode());
		ArgumentCaptor<PlanningRequest> captor = ArgumentCaptor.forClass(PlanningRequest.class);
		verify(plannerService).createPlan(captor.capture());
		assertEquals("project-1", captor.getValue().metadata().get("projectId"));
		assertEquals("workspace-1", captor.getValue().metadata().get("workspaceId"));
		assertEquals("/repo/jjx", captor.getValue().metadata().get("workspacePath"));
		assertEquals("READ_ONLY", captor.getValue().metadata().get("executionMode"));
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
			"Implement login", null, "Goal", null, "default"));
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
			"Implement login", null, "Goal", null, "default"));
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
			"Implement login", null, "Goal", null, "default"));
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

	@Test
	void shouldExecuteApprovedTaskThroughAgentCoordinator() {
		AgentCoordinatorService coordinator = mock(AgentCoordinatorService.class);
		service = new TaskCenterService(plannerService, approvalService, planRunRepository,
			coordinator, new AuditService(new InMemoryAuditRepository()));
		when(plannerService.createPlan(any()))
			.thenReturn(PlanningResult.success("hermes", null, PLAN));
		PlanApprovalRequest approval = new PlanApprovalRequest("approval-1", "task-1", PLAN,
			"hash", Instant.parse("2026-08-01T00:00:00Z"));
		approval.approve("user-1", Instant.parse("2026-08-01T00:05:00Z"));
		when(approvalService.create(any(), any())).thenReturn(approval);
		when(approvalService.get("approval-1")).thenReturn(approval);
		when(planRunRepository.findRunIdByApproval("approval-1")).thenReturn(null);

		TaskRecord task = service.createTask(new CreateTaskRequest(
			"Implement login", null, "Goal", null, "default"));
		TaskRecord approved = service.getTask(task.getTaskId()).orElseThrow();
		assertEquals(TaskStatus.APPROVED, approved.getStatus());

		TaskRecord executed = service.execute(task.getTaskId(), TaskType.CODE_GENERATION);

		assertEquals(TaskStatus.APPROVED, executed.getStatus());
		verify(coordinator).createCollaborationPlan(task.getTaskId(), TaskType.CODE_GENERATION);
	}

	@Test
	void shouldRejectExecuteWhenTaskIsNotApproved() {
		AgentCoordinatorService coordinator = mock(AgentCoordinatorService.class);
		service = new TaskCenterService(plannerService, approvalService, planRunRepository,
			coordinator, new AuditService(new InMemoryAuditRepository()));
		when(plannerService.createPlan(any()))
			.thenReturn(PlanningResult.success("hermes", null, PLAN));
		when(approvalService.create(any(), any())).thenReturn(
			new PlanApprovalRequest("approval-1", "task-1", PLAN, "hash",
				Instant.parse("2026-08-01T00:00:00Z")));
		when(planRunRepository.findRunIdByApproval("approval-1")).thenReturn(null);

		TaskRecord task = service.createTask(new CreateTaskRequest(
			"Implement login", null, "Goal", null, "default"));
		assertEquals(TaskStatus.PLANNING, task.getStatus());

		assertThrows(IllegalArgumentException.class,
			() -> service.execute(task.getTaskId()));
	}

	@Test
	void shouldRejectExecuteForUnknownTask() {
		AgentCoordinatorService coordinator = mock(AgentCoordinatorService.class);
		service = new TaskCenterService(plannerService, approvalService, planRunRepository,
			coordinator, new AuditService(new InMemoryAuditRepository()));

		assertThrows(IllegalArgumentException.class, () -> service.execute("missing"));
	}

	@Test
	void failedTaskRecoversToSuccessWhenPlanRunSucceedsAndTriggersHandoff() {
		// V1 Final Gate：FAILED Task + PlanRun SUCCESS（retry 恢复成功）
		// → refresh() → Task SUCCESS → completionHandoff 被触发（ChangeSet 生成）
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

		ExecutionCompletionHandoffService handoff = mock(ExecutionCompletionHandoffService.class);
		service.setCompletionHandoff(handoff);

		TaskRecord task = service.createTask(new CreateTaskRequest(
			"Implement login", null, "Goal", null, "default"));
		// 模拟：Task 在第一次 run 失败时已被标记 FAILED（终态）
		task.markFailed("Tool step failed: MAVEN (exit 1)");
		assertEquals(TaskStatus.FAILED, task.getStatus());

		Optional<TaskRecord> refreshed = service.getTask(task.getTaskId());

		assertTrue(refreshed.isPresent());
		assertEquals(TaskStatus.SUCCESS, refreshed.get().getStatus());
		assertEquals("run-1", refreshed.get().getPlanRunId());
		verify(handoff).project(task.getTaskId(), "run-1");
	}

	// ==================== V1-FINAL-CLOSEOUT ====================

	private TaskCenterService fullService(AgentCoordinatorService coordinator,
			TaskRepository repository) {
		return new TaskCenterService(plannerService, approvalService, planRunRepository,
			coordinator, AuditService.noop(), repository);
	}

	private TaskRecord approvedReadOnlyTask(String taskId, TaskRepository repository) {
		TaskRecord task = new TaskRecord(taskId, "Task", "Description", "p1", "w1",
			ExecutionMode.READ_ONLY);
		task.markApproved();
		repository.save(task);
		return task;
	}

	/** 1. READ_ONLY + 明确写任务 → MODE_CONFLICT（fail closed，不进入执行） */
	@Test
	void readOnlyCodeGenerationTaskFailsClosedWithModeConflict() {
		InMemoryTaskRepository repository = new InMemoryTaskRepository();
		AgentCoordinatorService coordinator = mock(AgentCoordinatorService.class);
		TaskCenterService svc = fullService(coordinator, repository);
		approvedReadOnlyTask("task-ro", repository);

		TaskModeConflictException ex = assertThrows(TaskModeConflictException.class,
			() -> svc.execute("task-ro", TaskType.CODE_GENERATION));
		assertTrue(ex.getMessage().contains("MODE_CONFLICT"));
		verify(coordinator, never()).createCollaborationPlan(any(), any());
	}

	/** 2. 正常 READ_ONLY 分析任务 → 不触发 MODE_CONFLICT，正常进入执行 */
	@Test
	void readOnlyAnalysisTaskDoesNotTriggerModeConflict() {
		InMemoryTaskRepository repository = new InMemoryTaskRepository();
		AgentCoordinatorService coordinator = mock(AgentCoordinatorService.class);
		TaskCenterService svc = fullService(coordinator, repository);
		approvedReadOnlyTask("task-an", repository);

		svc.execute("task-an", TaskType.TASK_ANALYSIS);

		verify(coordinator, times(1)).createCollaborationPlan("task-an", TaskType.TASK_ANALYSIS);
	}

	/** 3. RUNNING Task → Cancel → CANCELLED 且已有 PlanRun ABORTED（不再推进） */
	@Test
	void cancelRunningTaskMovesToCancelledAndAbortsPlanRun() {
		InMemoryTaskRepository repository = new InMemoryTaskRepository();
		AgentCoordinatorService coordinator = mock(AgentCoordinatorService.class);
		TaskCenterService svc = fullService(coordinator, repository);
		TaskRecord task = approvedReadOnlyTask("task-c1", repository);
		task.setPlanRunId("run-1");
		task.markRunning();
		repository.save(task);
		PlanRun run = new PlanRun("run-1", "approval-1", PLAN, List.of(),
			Instant.parse("2026-08-01T00:00:00Z"));
		run.markRunning(Instant.parse("2026-08-01T00:00:00Z"));
		when(planRunRepository.get("run-1")).thenReturn(run);

		TaskRecord cancelled = svc.cancel("task-c1");

		assertEquals(TaskStatus.CANCELLED, cancelled.getStatus());
		assertEquals(PlanRunStatus.ABORTED, run.getStatus());
		verify(planRunRepository).save(run);
	}

	/** 4. 重复 Cancel → 幂等 */
	@Test
	void cancelIsIdempotent() {
		InMemoryTaskRepository repository = new InMemoryTaskRepository();
		AgentCoordinatorService coordinator = mock(AgentCoordinatorService.class);
		TaskCenterService svc = fullService(coordinator, repository);
		TaskRecord task = approvedReadOnlyTask("task-c2", repository);
		task.markRunning();
		repository.save(task);

		TaskRecord first = svc.cancel("task-c2");
		TaskRecord second = svc.cancel("task-c2");

		assertEquals(TaskStatus.CANCELLED, first.getStatus());
		assertSame(first, second, "重复 Cancel 应返回同一 CANCELLED 记录");
		assertEquals(TaskStatus.CANCELLED, second.getStatus());
	}
}
