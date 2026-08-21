package com.aidevos.orchestrator.taskcenter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import com.aidevos.orchestrator.agentcoordinator.AgentCoordinatorService;
import com.aidevos.orchestrator.approval.ApprovalStatus;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.modelrouter.TaskType;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.PlanRunRepository;
import com.aidevos.orchestrator.plan.run.PlanRunStatus;
import com.aidevos.orchestrator.plan.schedule.PlanScheduler;
import com.aidevos.orchestrator.planner.PlanningRequest;
import com.aidevos.orchestrator.planner.PlanningResult;
import com.aidevos.orchestrator.planner.PlannerService;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.aidevos.orchestrator.analysis.AnalysisProjectionCoordinator;
import com.aidevos.orchestrator.change.ExecutionCompletionHandoffService;

/**
 * Unified task entry point: User Request -> Task -> Plan -> Approval ->
 * Execution -> Result. Planning, approval and execution are delegated to the
 * existing planner/approval/plan-run flow; this service only tracks the task
 * lifecycle and derives its status from those components.
 */
@Service
public class TaskCenterService {

	private static final String DEFAULT_PLANNER = "hermes";

	private final TaskRepository repository;
	private final PlannerService plannerService;
	private final PlanApprovalService approvalService;
	private final PlanRunRepository planRunRepository;
	private final AuditService auditService;
	private final PlanScheduler planScheduler;
	private AgentCoordinatorService agentCoordinatorService;
	private AnalysisProjectionCoordinator analysisProjectionCoordinator;
	private ExecutionCompletionHandoffService completionHandoff;

	@Autowired(required = false)
	public void setCompletionHandoff(@Lazy ExecutionCompletionHandoffService service) {
		this.completionHandoff = service;
	}

	public TaskCenterService(PlannerService plannerService,
			PlanApprovalService approvalService, PlanRunRepository planRunRepository) {
		this(plannerService, approvalService, planRunRepository, null, AuditService.noop(),
			new InMemoryTaskRepository(), null);
	}

	public TaskCenterService(PlannerService plannerService,
			PlanApprovalService approvalService, PlanRunRepository planRunRepository,
			@Lazy AgentCoordinatorService agentCoordinatorService, AuditService auditService) {
		this(plannerService, approvalService, planRunRepository, agentCoordinatorService,
			auditService, new InMemoryTaskRepository(), null);
	}

	public TaskCenterService(PlannerService plannerService,
			PlanApprovalService approvalService, PlanRunRepository planRunRepository,
			@Lazy AgentCoordinatorService agentCoordinatorService, AuditService auditService,
			TaskRepository repository) {
		this(plannerService, approvalService, planRunRepository, agentCoordinatorService,
			auditService, repository, null);
	}

	@Autowired
	public TaskCenterService(PlannerService plannerService,
			PlanApprovalService approvalService, PlanRunRepository planRunRepository,
			@Lazy AgentCoordinatorService agentCoordinatorService, AuditService auditService,
			TaskRepository repository, @Lazy PlanScheduler planScheduler) {
		this.plannerService = plannerService;
		this.approvalService = approvalService;
		this.planRunRepository = planRunRepository;
		this.agentCoordinatorService = agentCoordinatorService;
		this.auditService = auditService;
		this.repository = repository;
		this.planScheduler = planScheduler;
	}

	/**
	 * Associates the agent coordinator used by the closed-loop execution entry.
	 * Constructor injection is not possible because the coordinator also depends
	 * on this service; Spring resolves the cycle through @Lazy and tests wire it
	 * explicitly.
	 */
	public void setAgentCoordinatorService(AgentCoordinatorService agentCoordinatorService) {
		this.agentCoordinatorService = agentCoordinatorService;
	}

	@Autowired(required = false)
	public void setAnalysisProjectionCoordinator(AnalysisProjectionCoordinator coordinator) {
		this.analysisProjectionCoordinator = coordinator;
	}

	public TaskRecord createTask(CreateTaskRequest request) {
		return createTask(request, null);
	}

	public TaskRecord createTask(CreateTaskRequest request, String workspacePath) {
		return createTask(request, workspacePath, null);
	}

	public synchronized TaskRecord createTask(CreateTaskRequest request, String workspacePath,
			String sourceBacklogItemId) {
		String taskId = sourceBacklogItemId == null || sourceBacklogItemId.isBlank()
			? "task-" + UUID.randomUUID()
			: "task-" + UUID.nameUUIDFromBytes(("backlog:" + sourceBacklogItemId.trim())
				.getBytes(StandardCharsets.UTF_8));
		TaskRecord existing = repository.get(taskId);
		if (existing != null) return existing;
		TaskRecord task = new TaskRecord(taskId, request.name(), request.description(),
			request.projectId(), request.workspaceId(), request.executionMode(), sourceBacklogItemId,
			request.requestedModelId());
		repository.save(task);
		auditService.taskSubmitted(taskId,
			"User submitted task", Map.of("name", request.name(),
				"projectId", request.projectId() == null ? "default" : request.projectId()));
		try {
			PlanningResult result = plannerService.createPlan(new PlanningRequest(taskId,
				request.goal(), plannerName(request), null, null,
				planningInput(task, workspacePath), null,
				planningMetadata(task, workspacePath)));
			if (result.success() && result.plan() != null) {
				PlanApprovalRequest approval = approvalService.create(taskId, result.plan());
				task.markPlanning(approval.getId());
			}
			else {
				task.markFailed(joinErrors(result.errors()));
			}
		}
		catch (RuntimeException exception) {
			task.markFailed(errorMessage(exception));
		}
		repository.save(task);
		return task;
	}

	private Map<String, Object> planningInput(TaskRecord task, String workspacePath) {
		if (task.getExecutionMode() != ExecutionMode.READ_ONLY
				|| workspacePath == null || workspacePath.isBlank()) {
			return Map.of();
		}
		return Map.of("taskType", "project-analysis", "workspacePath", workspacePath,
			"executionMode", ExecutionMode.READ_ONLY.name());
	}

	/**
	 * Registers a task record directly without the interactive planning and
	 * approval flow. Used by the autonomous goal manager when it generates
	 * tasks for a goal into the orchestrator task pool.
	 */
	public TaskRecord registerTask(TaskRecord task) {
		if (task == null || task.getTaskId() == null || task.getTaskId().isBlank()) {
			throw new IllegalArgumentException("Task is required");
		}
		if (repository.get(task.getTaskId()) == null) {
			repository.save(task);
		}
		auditService.adminEvent(EventType.USER_OPERATION, "task", task.getTaskId(), "SYSTEM",
			"Autonomous goal generated task", Map.of("name",
				task.getName() == null ? "" : task.getName(), "projectId",
				task.getProjectId() == null ? "default" : task.getProjectId()));
		return task;
	}

	public List<TaskRecord> listTasks() {
		List<TaskRecord> result = new ArrayList<>(repository.list());
		result.forEach(this::refresh);
		result.sort(Comparator.comparing(TaskRecord::getCreatedAt).reversed());
		return result;
	}

	public Optional<TaskRecord> getTask(String taskId) {
		TaskRecord task = repository.get(taskId);
		if (task != null) {
			refresh(task);
		}
		return Optional.ofNullable(task);
	}

	/**
	 * Closed-loop development entry: an APPROVED task runs through the agent
	 * coordinator (collaboration plan -> coding -> testing) which updates the
	 * task status, records executions and persists memory. The legacy plan-run
	 * flow is untouched.
	 */
	public TaskRecord execute(String taskId) {
		return execute(taskId, TaskType.GENERAL);
	}

	public TaskRecord execute(String taskId, TaskType taskType) {
		TaskRecord task = repository.get(taskId);
		if (task == null) {
			throw new IllegalArgumentException("Task not found: " + taskId);
		}
		refresh(task);
		if (task.getStatus() != TaskStatus.APPROVED) {
			throw new IllegalArgumentException("Task is not approved: " + taskId);
		}
		if (agentCoordinatorService == null) {
			throw new IllegalStateException("Agent coordinator is not configured");
		}
		TaskType type = taskType == null ? TaskType.GENERAL : taskType;
		auditService.taskEvent(EventType.USER_OPERATION, taskId, task.getStatus().name(),
			TaskStatus.RUNNING.name(), "Task execution started",
			Map.of("taskType", type.name()));
		agentCoordinatorService.createCollaborationPlan(taskId, type);
		repository.save(task);
		return task;
	}

	public List<TaskRecord> listTasksByProject(String projectId) {
		List<TaskRecord> result = new ArrayList<>(repository.listByProject(projectId));
		result.forEach(this::refresh);
		return result;
	}

	public void saveTask(TaskRecord task) {
		if (task != null) {
			repository.save(task);
		}
	}

	public synchronized TaskRecord approve(String taskId, String approver) {
		TaskRecord task = requireTask(taskId);
		PlanApprovalRequest approval = requireTaskApproval(task);
		if (task.getStatus() != TaskStatus.PLANNING && task.getStatus() != TaskStatus.APPROVED
				&& task.getStatus() != TaskStatus.RUNNING) {
			throw new IllegalStateException("Task cannot be approved from state: " + task.getStatus());
		}
		if (approval.getStatus() == ApprovalStatus.REJECTED) {
			throw new IllegalStateException("Task plan approval has been rejected");
		}
		if (approval.getStatus() == ApprovalStatus.PENDING) {
			approval = approvalService.approve(approval.getId(), approver);
		}
		else if (approval.getStatus() != ApprovalStatus.APPROVED
				&& approval.getStatus() != ApprovalStatus.CONSUMED) {
			throw new IllegalStateException("Task plan approval cannot be approved from state: "
				+ approval.getStatus());
		}
		String runId = planRunRepository.findRunIdByApproval(approval.getId());
		if (runId == null) {
			if (planScheduler == null) {
				throw new IllegalStateException("Plan scheduler is not configured");
			}
			try {
				PlanRun run = planScheduler.start(approval.getId());
				runId = run.getId();
			}
			catch (IllegalStateException exception) {
				runId = planRunRepository.findRunIdByApproval(approval.getId());
				if (runId == null) {
					throw exception;
				}
			}
		}
		TaskStatus before = task.getStatus();
		task.setPlanRunId(runId);
		task.markRunning();
		repository.save(task);
		if (before != TaskStatus.RUNNING) {
			auditService.taskEvent(EventType.TASK_RUNNING, taskId, before.name(),
				TaskStatus.RUNNING.name(), "Approved task execution started",
				Map.of("approvalId", approval.getId(), "planRunId", runId));
		}
		return task;
	}

	public synchronized TaskRecord reject(String taskId, String approver, String reason) {
		TaskRecord task = requireTask(taskId);
		PlanApprovalRequest approval = requireTaskApproval(task);
		if (task.getStatus() != TaskStatus.PLANNING && task.getStatus() != TaskStatus.REJECTED) {
			throw new IllegalStateException("Task cannot be rejected from state: " + task.getStatus());
		}
		if (approval.getStatus() == ApprovalStatus.APPROVED
				|| approval.getStatus() == ApprovalStatus.CONSUMED) {
			throw new IllegalStateException("Approved task plan cannot be rejected");
		}
		if (approval.getStatus() == ApprovalStatus.PENDING) {
			approval = approvalService.reject(approval.getId(), approver, reason);
		}
		else if (approval.getStatus() != ApprovalStatus.REJECTED) {
			throw new IllegalStateException("Task plan approval cannot be rejected from state: "
				+ approval.getStatus());
		}
		if (planRunRepository.findRunIdByApproval(approval.getId()) != null) {
			throw new IllegalStateException("Rejected task plan already has a PlanRun");
		}
		TaskStatus before = task.getStatus();
		task.markRejected(approval.getRejectionReason());
		repository.save(task);
		if (before != TaskStatus.REJECTED) {
			auditService.taskEvent(EventType.TASK_REJECTED, taskId, before.name(),
				TaskStatus.REJECTED.name(), "Task plan rejected",
				Map.of("approvalId", approval.getId(), "reason",
					approval.getRejectionReason()));
		}
		return task;
	}

	private TaskRecord requireTask(String taskId) {
		TaskRecord task = repository.get(taskId);
		if (task == null) {
			throw new IllegalArgumentException("Task not found: " + taskId);
		}
		return task;
	}

	private PlanApprovalRequest requireTaskApproval(TaskRecord task) {
		if (task.getApprovalId() == null || task.getApprovalId().isBlank()) {
			throw new IllegalStateException("Task has no plan approval");
		}
		PlanApprovalRequest approval = approvalService.get(task.getApprovalId());
		if (approval == null) {
			throw new IllegalStateException("Task plan approval not found: "
				+ task.getApprovalId());
		}
		if (!task.getTaskId().equals(approval.getRequestId())) {
			throw new IllegalStateException("Task and plan approval requestId do not match");
		}
		return approval;
	}

	private void refresh(TaskRecord task) {
		TaskStatus before = task.getStatus();
		String beforeRunId = task.getPlanRunId();
		if (task.getStatus() == TaskStatus.SUCCESS || task.getStatus() == TaskStatus.COMPLETED
				|| task.getStatus() == TaskStatus.REJECTED) {
			return;
		}
		// V1 Final Gate：FAILED 是终态，唯一例外是 retry 后对应 PlanRun 已 SUCCESS 的恢复场景。
		if (task.getStatus() == TaskStatus.FAILED) {
			recoverFailedTaskIfRunSucceeded(task);
			return;
		}
		String approvalId = task.getApprovalId();
		if (approvalId == null) {
			return;
		}
		PlanApprovalRequest approval = approvalService.get(approvalId);
		if (approval == null) {
			return;
		}
		if (approval.getStatus() == ApprovalStatus.APPROVED
				&& task.getStatus() == TaskStatus.PLANNING) {
			task.markApproved();
			repository.save(task);
		}
		String runId = planRunRepository.findRunIdByApproval(approvalId);
		if (runId == null) {
			return;
		}
		PlanRun run = planRunRepository.get(runId);
		if (run == null) {
			return;
		}
		task.setPlanRunId(runId);
		switch (run.getStatus()) {
			case SUCCESS -> task.markSuccess();
			case FAILED, REPLAN_REQUIRED -> task.markFailed(run.getError());
			case RUNNING, WAITING_APPROVAL -> task.markRunning();
			default -> {
				// DRAFT: the plan run exists but has not started; keep current status.
			}
		}
		if (before != task.getStatus() || !java.util.Objects.equals(beforeRunId, task.getPlanRunId())) {
			repository.save(task);
			if (before != TaskStatus.SUCCESS && task.getStatus() == TaskStatus.SUCCESS
					&& analysisProjectionCoordinator != null) {
				analysisProjectionCoordinator.schedule(task.getTaskId());
			}
			if (before != TaskStatus.SUCCESS && task.getStatus() == TaskStatus.SUCCESS
					&& completionHandoff != null) {
				completionHandoff.project(task.getTaskId(), runId);
			}
		}
	}

	/**
	 * V1 Final Gate：FAILED Task 的恢复场景。仅当对应 PlanRun 已 SUCCESS（retry 恢复成功）
	 * 时，允许 Task 从 FAILED 更新为 SUCCESS，并复用现有 completionHandoff 生成 ChangeSet。
	 * 其他终态语义（SUCCESS/COMPLETED/REJECTED）保持不变；PlanRun 非 SUCCESS 时保持 FAILED。
	 */
	private void recoverFailedTaskIfRunSucceeded(TaskRecord task) {
		String approvalId = task.getApprovalId();
		if (approvalId == null) {
			return;
		}
		String runId = planRunRepository.findRunIdByApproval(approvalId);
		if (runId == null) {
			return;
		}
		PlanRun run = planRunRepository.get(runId);
		if (run == null || run.getStatus() != PlanRunStatus.SUCCESS) {
			return;
		}
		task.setPlanRunId(runId);
		task.markSuccess();
		repository.save(task);
		if (analysisProjectionCoordinator != null) {
			analysisProjectionCoordinator.schedule(task.getTaskId());
		}
		if (completionHandoff != null) {
			completionHandoff.project(task.getTaskId(), runId);
		}
	}

	private Map<String, Object> planningMetadata(TaskRecord task, String workspacePath) {
		Map<String, Object> metadata = new java.util.LinkedHashMap<>();
		metadata.put("projectId", task.getProjectId());
		metadata.put("workspaceId", task.getWorkspaceId() == null ? "" : task.getWorkspaceId());
		metadata.put("workspacePath", workspacePath == null ? "" : workspacePath);
		metadata.put("executionMode", task.getExecutionMode().name());
		metadata.put("requestedModelId", task.getRequestedModelId() == null ? "" : task.getRequestedModelId());
		if (task.getExecutionMode() == ExecutionMode.READ_ONLY
				&& workspacePath != null && !workspacePath.isBlank()) {
			metadata.put("taskType", "project-analysis");
		}
		return Map.copyOf(metadata);
	}

	private String plannerName(CreateTaskRequest request) {
		return request.plannerName() == null || request.plannerName().isBlank()
			? DEFAULT_PLANNER : request.plannerName();
	}

	private String joinErrors(List<String> errors) {
		return errors == null || errors.isEmpty()
			? "Planning failed" : String.join(", ", errors);
	}

	private String errorMessage(RuntimeException exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
			? exception.getClass().getSimpleName() : exception.getMessage();
	}
}
