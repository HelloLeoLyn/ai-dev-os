package com.aidevos.orchestrator.repair;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.executor.codex.CodexExecutor;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.MemoryType;
import com.aidevos.orchestrator.planner.PlannerService;
import com.aidevos.orchestrator.planner.PlanningRequest;
import com.aidevos.orchestrator.planner.PlanningResult;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.testagent.CreateTestRequest;
import com.aidevos.orchestrator.testagent.TestAgentService;
import com.aidevos.orchestrator.testagent.TestPlan;
import com.aidevos.orchestrator.testagent.TestStatus;
import com.aidevos.orchestrator.testagent.TestType;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.git.GitDiff;
import org.springframework.stereotype.Service;

/**
 * Automatic repair loop for failed tasks: TestAgent failure -> FailureContext
 * -> Hermes analysis -> Codex fix in the workspace -> TestAgent re-verification.
 * The loop is bounded by RepairPolicy.MAX_RETRY; a repair that never passes
 * verification becomes FAILED (never runs forever). Scheduler, Worker and
 * ExecutionEngine are not touched.
 */
@Service
public class RepairCoordinator {

	private static final String HERMES_PLANNER = "hermes";
	private static final int LOG_LIMIT = 2000;

	private final Map<String, RepairTask> repairs = new ConcurrentHashMap<>();
	private final TaskCenterService taskCenterService;
	private final TestAgentService testAgentService;
	private final PlannerService plannerService;
	private final CodexExecutor codexExecutor;
	private final WorkspaceService workspaceService;
	private final MemoryService memoryService;
	private final AuditService auditService;

	public RepairCoordinator(TaskCenterService taskCenterService,
			TestAgentService testAgentService, PlannerService plannerService,
			CodexExecutor codexExecutor, WorkspaceService workspaceService,
			MemoryService memoryService, AuditService auditService) {
		this.taskCenterService = taskCenterService;
		this.testAgentService = testAgentService;
		this.plannerService = plannerService;
		this.codexExecutor = codexExecutor;
		this.workspaceService = workspaceService;
		this.memoryService = memoryService;
		this.auditService = auditService;
	}

	public RepairTask start(String taskId) {
		TaskRecord task = taskCenterService.getTask(taskId)
			.orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
		TestPlan failedTest = latestFailedTest(taskId);
		String workspaceId = task.getWorkspaceId();
		FailureContext failureContext = new FailureContext(taskId, workspaceId,
			failedTest.getTestId(), failedTest.getErrorMessage(),
			truncate(failedTest.getLogs()), truncate(failedTest.getLogs()),
			gitDiff(workspaceId), Instant.now());
		RepairTask repairTask = new RepairTask("repair-" + UUID.randomUUID(), taskId,
			workspaceId, failureContext);
		repairs.put(taskId, repairTask);
		auditService.repairEvent(EventType.REPAIR_STARTED, taskId, repairTask.getRepairId(),
			null, RepairStatus.PENDING.name(), "Repair started",
			Map.of("testId", failedTest.getTestId()));
		repair(repairTask, task);
		return repairTask;
	}

	public Optional<RepairTask> get(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(repairs.get(taskId));
	}

	private void repair(RepairTask repairTask, TaskRecord task) {
		String lastError = null;
		while (repairTask.getRetryCount() < RepairPolicy.MAX_RETRY) {
			try {
				analyze(repairTask, task);
				fix(repairTask, task);
				verify(repairTask, task);
				String result = "Repair succeeded after "
					+ (repairTask.getRetryCount() + 1) + " attempt(s)";
				repairTask.markSuccess(result);
				auditService.repairEvent(EventType.REPAIR_SUCCESS, task.getTaskId(),
					repairTask.getRepairId(), RepairStatus.VERIFYING.name(),
					RepairStatus.SUCCESS.name(), result, Map.of());
				rememberResolution(repairTask, task, result);
				return;
			}
			catch (RuntimeException exception) {
				lastError = errorMessage(exception);
				repairTask.incrementRetry();
			}
		}
		repairTask.markFailed("Repair failed: " + value(lastError));
		auditService.repairEvent(EventType.REPAIR_FAILED, task.getTaskId(),
			repairTask.getRepairId(), RepairStatus.VERIFYING.name(), RepairStatus.FAILED.name(),
			"Repair failed after " + RepairPolicy.MAX_RETRY + " attempts", Map.of());
		rememberFailure(repairTask, task, value(lastError));
	}

	private void analyze(RepairTask repairTask, TaskRecord task) {
		repairTask.markAnalyzing();
		auditService.repairEvent(EventType.REPAIR_ANALYZING, task.getTaskId(),
			repairTask.getRepairId(), RepairStatus.PENDING.name(), RepairStatus.ANALYZING.name(),
			"Analyzing failure", Map.of());
		FailureContext context = repairTask.getFailureContext();
		String goal = "修复任务 " + task.getTaskId() + " 的测试失败。" + System.lineSeparator()
			+ "错误: " + value(context.errorMessage()) + System.lineSeparator()
			+ "测试报告: " + value(context.testReport()) + System.lineSeparator()
			+ "Git diff: " + value(context.gitDiff());
		PlanningResult result = plannerService.createPlan(new PlanningRequest(task.getTaskId(),
			goal, HERMES_PLANNER, null, null, null, null, null));
		if (!result.success() || result.plan() == null) {
			throw new IllegalStateException("Repair planning failed: " + joinErrors(result.errors()));
		}
	}

	private void fix(RepairTask repairTask, TaskRecord task) {
		repairTask.markFixing();
		auditService.repairEvent(EventType.REPAIR_FIXING, task.getTaskId(),
			repairTask.getRepairId(), RepairStatus.ANALYZING.name(), RepairStatus.FIXING.name(),
			"Applying codex fix", Map.of());
		String workspacePath = workspacePath(repairTask.getWorkspaceId());
		if (workspacePath == null) {
			throw new IllegalStateException("Repair requires a workspace");
		}
		ExecutionContext context = new ExecutionContext();
		context.setExecutionId("repair-" + UUID.randomUUID());
		context.setTaskId(task.getTaskId());
		context.setTaskName(task.getName());
		context.setProjectId(task.getProjectId());
		context.setDescription(fixPrompt(repairTask));
		context.setInput(fixPrompt(repairTask));
		context.setAgentName("codex");
		context.setWorkspace(workspacePath);
		context.getParameters().put("workspace", workspacePath);
		ExecutionResult result = codexExecutor.execute(context);
		if (result.isApprovalRequired()) {
			throw new IllegalStateException("Repair requires approval: "
				+ value(result.getApprovalId()));
		}
		if (!result.isSuccess()) {
			throw new IllegalStateException("Codex repair failed: " + message(result));
		}
	}

	private void verify(RepairTask repairTask, TaskRecord task) {
		repairTask.markVerifying();
		auditService.repairEvent(EventType.REPAIR_VERIFYING, task.getTaskId(),
			repairTask.getRepairId(), RepairStatus.FIXING.name(), RepairStatus.VERIFYING.name(),
			"Re-running tests", Map.of());
		TestPlan plan = testAgentService.createTest(new CreateTestRequest(task.getTaskId(),
			TestType.UNIT_TEST, null, null, task.getProjectId()));
		if (TestStatus.SUCCESS.equals(plan.getStatus())) {
			return;
		}
		throw new IllegalStateException("Tests failed: " + value(plan.getErrorMessage()));
	}

	private String fixPrompt(RepairTask repairTask) {
		FailureContext context = repairTask.getFailureContext();
		return "修复当前工作区中导致测试失败的缺陷。" + System.lineSeparator()
			+ "禁止运行 git commit / git push / git reset 或破坏性清理命令。" + System.lineSeparator()
			+ "错误: " + value(context.errorMessage()) + System.lineSeparator()
			+ "测试报告: " + value(context.testReport()) + System.lineSeparator()
			+ "Git diff: " + value(context.gitDiff());
	}

	private TestPlan latestFailedTest(String taskId) {
		return testAgentService.listTests().stream()
			.filter(plan -> taskId.equals(plan.getTaskId()))
			.filter(plan -> plan.getStatus() == TestStatus.FAILED)
			.max(Comparator.comparing(TestPlan::getCreatedAt))
			.orElseThrow(() -> new IllegalArgumentException(
				"No failed test found for task: " + taskId));
	}

	private String gitDiff(String workspaceId) {
		if (workspaceId == null || workspaceId.isBlank()) {
			return "";
		}
		try {
			GitDiff diff = workspaceService.getGitDiff(workspaceId);
			return diff.getStat() == null ? "" : diff.getStat();
		}
		catch (RuntimeException exception) {
			return "";
		}
	}

	private String workspacePath(String workspaceId) {
		if (workspaceId == null || workspaceId.isBlank()) {
			return null;
		}
		return workspaceService.getWorkspace(workspaceId)
			.map(Workspace::getPath)
			.orElse(null);
	}

	private void rememberResolution(RepairTask repairTask, TaskRecord task, String solution) {
		try {
			MemoryRecord existing = findBugRecord(task.getProjectId(),
				"bug:repair:" + task.getTaskId());
			if (existing != null) {
				existing.setResolved(true);
				existing.setSolution(solution);
				memoryService.update(existing.getId(), existing);
			}
			else {
				MemoryRecord record = bugRecord(repairTask, task, solution);
				record.setResolved(true);
				record.setSolution(solution);
				memoryService.create(record);
			}
			MemoryRecord experience = new MemoryRecord();
			experience.setProjectId(task.getProjectId());
			experience.setType(MemoryType.AGENT_EXPERIENCE);
			experience.setKey("experience:repair:" + task.getTaskId());
			experience.setContent("任务: " + task.getTaskId() + System.lineSeparator()
				+ "修复: " + solution + System.lineSeparator()
				+ "错误: " + value(repairTask.getFailureContext().errorMessage()));
			memoryService.create(experience);
		}
		catch (RuntimeException exception) {
			// Memory must not break the repair flow.
		}
	}

	private void rememberFailure(RepairTask repairTask, TaskRecord task, String error) {
		try {
			MemoryRecord record = bugRecord(repairTask, task, error);
			record.setResolved(false);
			memoryService.create(record);
		}
		catch (RuntimeException exception) {
			// Memory must not break the repair flow.
		}
	}

	private MemoryRecord bugRecord(RepairTask repairTask, TaskRecord task, String detail) {
		MemoryRecord record = new MemoryRecord();
		record.setProjectId(task.getProjectId());
		record.setType(MemoryType.BUG_RECORD);
		record.setKey("bug:repair:" + task.getTaskId());
		record.setContent("错误信息: " + detail + System.lineSeparator()
			+ "任务: " + task.getTaskId() + System.lineSeparator()
			+ "测试: " + value(repairTask.getFailureContext().testId()) + System.lineSeparator()
			+ "工作区: " + value(repairTask.getWorkspaceId()));
		return record;
	}

	private MemoryRecord findBugRecord(String projectId, String key) {
		return memoryService.list(projectId, MemoryType.BUG_RECORD).stream()
			.filter(record -> key.equals(record.getKey()))
			.findFirst()
			.orElse(null);
	}

	private String joinErrors(List<String> errors) {
		return errors == null || errors.isEmpty()
			? "unknown" : String.join(", ", errors);
	}

	private String message(ExecutionResult result) {
		return result.getMessage() == null || result.getMessage().isBlank()
			? "Codex execution failed" : result.getMessage();
	}

	private String errorMessage(RuntimeException exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
			? exception.getClass().getSimpleName() : exception.getMessage();
	}

	private String truncate(String value) {
		if (value == null) {
			return null;
		}
		return value.length() <= LOG_LIMIT ? value : value.substring(0, LOG_LIMIT);
	}

	private String value(String value) {
		return value == null ? "" : value;
	}
}
