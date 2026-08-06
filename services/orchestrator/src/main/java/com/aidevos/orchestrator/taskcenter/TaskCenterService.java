package com.aidevos.orchestrator.taskcenter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.aidevos.orchestrator.approval.ApprovalStatus;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.PlanRunRepository;
import com.aidevos.orchestrator.planner.PlanningRequest;
import com.aidevos.orchestrator.planner.PlanningResult;
import com.aidevos.orchestrator.planner.PlannerService;
import org.springframework.stereotype.Service;

/**
 * Unified task entry point: User Request -> Task -> Plan -> Approval ->
 * Execution -> Result. Planning, approval and execution are delegated to the
 * existing planner/approval/plan-run flow; this service only tracks the task
 * lifecycle and derives its status from those components.
 */
@Service
public class TaskCenterService {

	private static final String DEFAULT_PLANNER = "hermes";

	private final Map<String, TaskRecord> tasks = new ConcurrentHashMap<>();
	private final PlannerService plannerService;
	private final PlanApprovalService approvalService;
	private final PlanRunRepository planRunRepository;

	public TaskCenterService(PlannerService plannerService,
			PlanApprovalService approvalService, PlanRunRepository planRunRepository) {
		this.plannerService = plannerService;
		this.approvalService = approvalService;
		this.planRunRepository = planRunRepository;
	}

	public TaskRecord createTask(CreateTaskRequest request) {
		String taskId = "task-" + UUID.randomUUID();
		TaskRecord task = new TaskRecord(taskId, request.name(), request.description(),
			request.projectId());
		tasks.put(taskId, task);
		try {
			PlanningResult result = plannerService.createPlan(new PlanningRequest(taskId,
				request.goal(), plannerName(request), null, null, null, null, null));
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
		return task;
	}

	public List<TaskRecord> listTasks() {
		List<TaskRecord> result = new ArrayList<>(tasks.values());
		result.forEach(this::refresh);
		result.sort(Comparator.comparing(TaskRecord::getCreatedAt).reversed());
		return result;
	}

	public Optional<TaskRecord> getTask(String taskId) {
		TaskRecord task = tasks.get(taskId);
		if (task != null) {
			refresh(task);
		}
		return Optional.ofNullable(task);
	}

	private void refresh(TaskRecord task) {
		if (task.getStatus() == TaskStatus.SUCCESS || task.getStatus() == TaskStatus.FAILED) {
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
