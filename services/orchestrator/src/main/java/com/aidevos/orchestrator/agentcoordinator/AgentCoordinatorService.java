package com.aidevos.orchestrator.agentcoordinator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import com.aidevos.orchestrator.agentcapability.AgentCapabilityResolver;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.MemoryType;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.modelrouter.ModelRouterService;
import com.aidevos.orchestrator.modelrouter.ResolvedModel;
import com.aidevos.orchestrator.modelrouter.TaskType;
import com.aidevos.orchestrator.planner.PlannerService;
import com.aidevos.orchestrator.planner.PlanningRequest;
import com.aidevos.orchestrator.planner.PlanningResult;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import com.aidevos.orchestrator.testagent.CreateTestRequest;
import com.aidevos.orchestrator.testagent.TestAgentService;
import com.aidevos.orchestrator.testagent.TestPlan;
import com.aidevos.orchestrator.testagent.TestStatus;
import com.aidevos.orchestrator.testagent.TestType;
import org.springframework.stereotype.Service;

/**
 * Agent collaboration coordinator. Builds a sequential execution plan for a
 * Task Center task: the task type is matched to an agent capability through
 * AgentCapabilityResolver, which dynamically selects the registered agent
 * (AgentRegistry) for that capability; the plan runs the collaboration chain
 * from that agent onwards. Reuses the existing TaskCenter, ModelRouter,
 * Planner/Hermes, executors and TestAgent; Scheduler, Worker and
 * ExecutionEngine are not modified. Steps run sequentially and stop at the
 * first failure (downstream steps remain PENDING).
 */
@Service
public class AgentCoordinatorService {

	private static final String PLANNING_CAPABILITY = "planning";
	private static final String CODING_CAPABILITY = "coding";
	private static final String BROWSER_CAPABILITY = "browser";
	private static final String TESTING_CAPABILITY = "testing";
	private static final String HERMES_PLANNER = "hermes";
	private static final int LOG_LIMIT = 2000;

	private final Map<String, List<AgentExecutionPlan>> plans = new ConcurrentHashMap<>();
	private final TaskCenterService taskCenterService;
	private final ModelRouterService modelRouterService;
	private final PlannerService plannerService;
	private final ExecutorManager executorManager;
	private final TestAgentService testAgentService;
	private final AuditService auditService;
	private final AgentCapabilityResolver capabilityResolver;
	private final MemoryService memoryService;
	private final ExecutionRecordManager executionRecordManager;

	public AgentCoordinatorService(TaskCenterService taskCenterService,
			ModelRouterService modelRouterService, PlannerService plannerService,
			ExecutorManager executorManager, TestAgentService testAgentService,
			AuditService auditService, AgentCapabilityResolver capabilityResolver,
			MemoryService memoryService, ExecutionRecordManager executionRecordManager) {
		this.taskCenterService = taskCenterService;
		this.modelRouterService = modelRouterService;
		this.plannerService = plannerService;
		this.executorManager = executorManager;
		this.testAgentService = testAgentService;
		this.auditService = auditService;
		this.capabilityResolver = capabilityResolver;
		this.memoryService = memoryService;
		this.executionRecordManager = executionRecordManager;
	}

	/**
	 * Resolves the agent responsible for a task type through the capability
	 * registry. Unknown types fall back to the planning capability.
	 */
	public String selectAgent(TaskType taskType) {
		return capabilityResolver.resolveAgent(taskType)
			.map(AgentDefinition::getName)
			.orElse(null);
	}

	public List<AgentExecutionPlan> createCollaborationPlan(String taskId, TaskType taskType) {
		TaskRecord task = taskCenterService.getTask(taskId)
			.orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
		TaskType type = taskType == null ? TaskType.GENERAL : taskType;
		ResolvedModel model = modelRouterService.route(type);
		String planId = "plan-" + UUID.randomUUID();
		List<AgentExecutionPlan> steps = buildSteps(planId, taskId, type);
		plans.put(taskId, steps);
		auditService.agentPlanEvent(EventType.AGENT_PLAN_CREATED, planId, taskId,
			steps.getFirst().getAgentId(), 0, null, "PENDING",
			"Agent collaboration plan created",
			Map.of("taskType", type.name(), "model", model.model()));
		execute(steps, task, model);
		finalizeOutcome(steps, task);
		return List.copyOf(steps);
	}

	public Optional<List<AgentExecutionPlan>> getCollaborationPlan(String taskId) {
		List<AgentExecutionPlan> steps = plans.get(taskId);
		if (steps == null) {
			return Optional.empty();
		}
		List<AgentExecutionPlan> ordered = new ArrayList<>(steps);
		ordered.sort(Comparator.comparingInt(AgentExecutionPlan::getStep));
		return Optional.of(List.copyOf(ordered));
	}

	private List<AgentExecutionPlan> buildSteps(String planId, String taskId, TaskType taskType) {
		List<AgentExecutionPlan> steps = new ArrayList<>();
		int stepNumber = 1;
		for (String capability : capabilitiesFor(taskType)) {
			AgentDefinition agent = capabilityResolver.resolveAgent(capability)
				.orElseThrow(() -> new IllegalStateException(
					"No agent found for capability: " + capability));
			steps.add(new AgentExecutionPlan(planId, taskId, agent.getName(), stepNumber++,
				capability));
		}
		return steps;
	}

	private List<String> capabilitiesFor(TaskType taskType) {
		return switch (taskType == null ? TaskType.GENERAL : taskType) {
			case TASK_ANALYSIS -> List.of(PLANNING_CAPABILITY, CODING_CAPABILITY,
				BROWSER_CAPABILITY, TESTING_CAPABILITY);
			case CODE_GENERATION -> List.of(CODING_CAPABILITY, BROWSER_CAPABILITY,
				TESTING_CAPABILITY);
			case BROWSER_TEST -> List.of(BROWSER_CAPABILITY, TESTING_CAPABILITY);
			case TEST_VERIFY -> List.of(TESTING_CAPABILITY);
			default -> List.of(PLANNING_CAPABILITY, CODING_CAPABILITY,
				BROWSER_CAPABILITY, TESTING_CAPABILITY);
		};
	}

	private void execute(List<AgentExecutionPlan> steps, TaskRecord task, ResolvedModel model) {
		for (AgentExecutionPlan step : steps) {
			executeStep(step, task, model);
			if (step.getStatus() == AgentPlanStatus.FAILED) {
				return;
			}
		}
	}

	private void executeStep(AgentExecutionPlan step, TaskRecord task, ResolvedModel model) {
		step.markRunning();
		auditService.agentPlanEvent(EventType.AGENT_PLAN_STARTED, step.getPlanId(),
			step.getTaskId(), step.getAgentId(), step.getStep(), AgentPlanStatus.PENDING.name(),
			AgentPlanStatus.RUNNING.name(), "Agent step started", Map.of());
		if (CODING_CAPABILITY.equals(step.getCapability())) {
			String from = task.getStatus().name();
			task.markCoding();
			auditService.taskEvent(EventType.USER_OPERATION, task.getTaskId(), from,
				TaskStatus.CODING.name(), "Coding started",
				Map.of("agent", step.getAgentId()));
		}
		else if (TESTING_CAPABILITY.equals(step.getCapability())) {
			String from = task.getStatus().name();
			task.markTesting();
			auditService.taskEvent(EventType.USER_OPERATION, task.getTaskId(), from,
				TaskStatus.TESTING.name(), "Testing started",
				Map.of("agent", step.getAgentId()));
		}
		try {
			String result = switch (step.getCapability()) {
				case PLANNING_CAPABILITY -> runPlanning(task, model);
				case CODING_CAPABILITY -> runCoding(task, step.getAgentId());
				case BROWSER_CAPABILITY -> runBrowser(task, step.getAgentId());
				case TESTING_CAPABILITY -> runTesting(task);
				default -> throw new IllegalStateException(
					"Unsupported capability: " + step.getCapability());
			};
			step.markSuccess(truncate(result));
			auditService.agentPlanEvent(EventType.AGENT_PLAN_SUCCEEDED, step.getPlanId(),
				step.getTaskId(), step.getAgentId(), step.getStep(),
				AgentPlanStatus.RUNNING.name(), AgentPlanStatus.SUCCESS.name(),
				"Agent step succeeded", Map.of());
		}
		catch (RuntimeException exception) {
			String error = errorMessage(exception);
			step.markFailed(error);
			auditService.agentPlanEvent(EventType.AGENT_PLAN_FAILED, step.getPlanId(),
				step.getTaskId(), step.getAgentId(), step.getStep(),
				AgentPlanStatus.RUNNING.name(), AgentPlanStatus.FAILED.name(),
				"Agent step failed: " + error, Map.of());
		}
	}

	private String runPlanning(TaskRecord task, ResolvedModel model) {
		String goal = task.getDescription() == null || task.getDescription().isBlank()
			? task.getName() : task.getDescription();
		PlanningResult result = plannerService.createPlan(new PlanningRequest(task.getTaskId(),
			goal, HERMES_PLANNER, model == null ? null : model.model(), null, null, null, null));
		if (!result.success() || result.plan() == null) {
			throw new IllegalStateException("Planning failed: " + joinErrors(result.errors()));
		}
		return "Plan created: " + result.plan().id();
	}

	private String runCoding(TaskRecord task, String agentName) {
		return summarize(executeAgent(agentName, task));
	}

	private String runBrowser(TaskRecord task, String agentName) {
		return summarize(executeAgent(agentName, task));
	}

	private ExecutionResult executeAgent(String agentName, TaskRecord task) {
		AgentExecutor executor = executorManager.getExecutor(agentName);
		if (executor == null) {
			throw new IllegalStateException("Executor not found for agent: " + agentName);
		}
		String executionId = "exec-" + UUID.randomUUID();
		ExecutionContext context = new ExecutionContext();
		context.setExecutionId(executionId);
		context.setTaskId(task.getTaskId());
		context.setTaskName(task.getName());
		context.setProjectId(task.getProjectId());
		context.setDescription(task.getDescription());
		context.setInput(task.getDescription() == null || task.getDescription().isBlank()
			? task.getName() : task.getDescription());
		context.setAgentName(agentName);
		ExecutionResult result = executor.execute(context);
		saveExecutionRecord(task, agentName, executionId, result);
		return result;
	}

	/**
	 * Writes back the closed-loop outcome: task status, task-level audit event
	 * and project memory (HISTORY_TASK on success, BUG_RECORD on failure).
	 */
	private void finalizeOutcome(List<AgentExecutionPlan> steps, TaskRecord task) {
		boolean succeeded = steps.stream()
			.allMatch(step -> step.getStatus() == AgentPlanStatus.SUCCESS);
		if (succeeded) {
			String from = task.getStatus().name();
			task.markCompleted();
			auditService.taskEvent(EventType.USER_OPERATION, task.getTaskId(), from,
				TaskStatus.COMPLETED.name(), "Task completed",
				Map.of("steps", steps.size()));
			saveHistoryTask(task, steps);
		}
		else {
			String from = task.getStatus().name();
			String error = steps.stream()
				.filter(step -> step.getStatus() == AgentPlanStatus.FAILED)
				.map(AgentExecutionPlan::getResult)
				.findFirst()
				.orElse("Agent collaboration failed");
			task.markFailed(error);
			auditService.taskEvent(EventType.USER_OPERATION, task.getTaskId(), from,
				TaskStatus.FAILED.name(), "Task failed: " + error, Map.of());
			saveBugRecord(task, error);
		}
	}

	private void saveExecutionRecord(TaskRecord task, String agentName, String executionId,
			ExecutionResult result) {
		try {
			ExecutionRecord record = new ExecutionRecord();
			record.setId(executionId);
			record.setExecutionId(executionId);
			record.setTaskId(task.getTaskId());
			record.setAgentName(agentName);
			record.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
			record.setMessage(result.getMessage());
			record.setOutput(result.getOutput());
			Instant now = Instant.now();
			record.setStartedAt(now);
			record.setCompletedAt(now);
			executionRecordManager.save(record);
		}
		catch (RuntimeException exception) {
			// Execution record persistence must not break the agent flow.
		}
	}

	private void saveHistoryTask(TaskRecord task, List<AgentExecutionPlan> steps) {
		try {
			MemoryRecord record = new MemoryRecord();
			record.setProjectId(task.getProjectId());
			record.setType(MemoryType.HISTORY_TASK);
			record.setKey("history:task:" + task.getTaskId());
			record.setContent(historyContent(task, steps));
			memoryService.create(record);
		}
		catch (RuntimeException exception) {
			// Memory must not break the task flow; the failure is already audited.
		}
	}

	private void saveBugRecord(TaskRecord task, String error) {
		try {
			MemoryRecord record = new MemoryRecord();
			record.setProjectId(task.getProjectId());
			record.setType(MemoryType.BUG_RECORD);
			record.setKey("bug:task:" + task.getTaskId());
			record.setContent("错误信息: " + error + System.lineSeparator()
				+ "任务: " + task.getTaskId() + System.lineSeparator()
				+ "项目: " + task.getProjectId());
			memoryService.create(record);
		}
		catch (RuntimeException exception) {
			// Memory must not break the task flow; the failure is already audited.
		}
	}

	private String historyContent(TaskRecord task, List<AgentExecutionPlan> steps) {
		StringBuilder builder = new StringBuilder();
		builder.append("任务: ").append(task.getTaskId()).append(System.lineSeparator())
			.append("名称: ").append(task.getName()).append(System.lineSeparator())
			.append("项目: ").append(task.getProjectId()).append(System.lineSeparator())
			.append("执行步骤:").append(System.lineSeparator());
		for (AgentExecutionPlan step : steps) {
			builder.append("  ").append(step.getStep()).append(". ")
				.append(step.getCapability()).append(" -> ").append(step.getAgentId())
				.append(" [").append(step.getStatus()).append("]")
				.append(System.lineSeparator());
		}
		return builder.toString();
	}

	private String runTesting(TaskRecord task) {
		TestPlan plan = testAgentService.createTest(new CreateTestRequest(task.getTaskId(),
			TestType.UNIT_TEST, null, null, null));
		if (TestStatus.SUCCESS.equals(plan.getStatus())) {
			return "Tests passed: " + plan.getTestId();
		}
		String error = plan.getErrorMessage() == null || plan.getErrorMessage().isBlank()
			? plan.getStatus().name() : plan.getErrorMessage();
		throw new IllegalStateException("Tests failed: " + error);
	}

	private String summarize(ExecutionResult result) {
		if (result == null) {
			throw new IllegalStateException("Agent returned no result");
		}
		if (!result.isSuccess()) {
			String message = result.getMessage() == null || result.getMessage().isBlank()
				? "Agent execution failed" : result.getMessage();
			throw new IllegalStateException(message);
		}
		String output = result.getOutput();
		if (output != null && !output.isBlank()) {
			return output;
		}
		return result.getMessage() == null || result.getMessage().isBlank()
			? "Agent execution succeeded" : result.getMessage();
	}

	private String joinErrors(List<String> errors) {
		return errors == null || errors.isEmpty() ? "unknown" : String.join(", ", errors);
	}

	private String truncate(String value) {
		if (value == null) {
			return null;
		}
		return value.length() <= LOG_LIMIT ? value : value.substring(0, LOG_LIMIT);
	}

	private String errorMessage(RuntimeException exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
			? exception.getClass().getSimpleName() : exception.getMessage();
	}
}
