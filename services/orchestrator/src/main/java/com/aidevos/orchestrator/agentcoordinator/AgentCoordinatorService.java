package com.aidevos.orchestrator.agentcoordinator;

import java.util.ArrayList;
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
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.modelrouter.ModelRouterService;
import com.aidevos.orchestrator.modelrouter.ResolvedModel;
import com.aidevos.orchestrator.modelrouter.TaskType;
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
import org.springframework.stereotype.Service;

/**
 * Agent collaboration coordinator. Builds a sequential execution plan for a
 * Task Center task: the task type selects the responsible agent (initial
 * rules: TASK_ANALYSIS -&gt; Hermes, CODE_GENERATION -&gt; Codex,
 * BROWSER_TEST -&gt; OpenClaw, TEST_VERIFY -&gt; TestAgent) and the plan runs
 * the collaboration chain from that agent onwards. Reuses the existing
 * TaskCenter, ModelRouter, Planner/Hermes, executors and TestAgent; Scheduler,
 * Worker and ExecutionEngine are not modified. Steps run sequentially and stop
 * at the first failure (downstream steps remain PENDING).
 */
@Service
public class AgentCoordinatorService {

	public static final String HERMES_AGENT = "hermes";
	public static final String CODEX_AGENT = "codex";
	public static final String OPENCLAW_AGENT = "openclaw";
	public static final String TESTAGENT_AGENT = "testagent";

	private static final String HERMES_PLANNER = "hermes";
	private static final String CODEX_AGENT_NAME = "coder";
	private static final String OPENCLAW_AGENT_NAME = "tester";
	private static final int LOG_LIMIT = 2000;

	private final Map<String, List<AgentExecutionPlan>> plans = new ConcurrentHashMap<>();
	private final TaskCenterService taskCenterService;
	private final ModelRouterService modelRouterService;
	private final PlannerService plannerService;
	private final ExecutorManager executorManager;
	private final TestAgentService testAgentService;
	private final AuditService auditService;

	public AgentCoordinatorService(TaskCenterService taskCenterService,
			ModelRouterService modelRouterService, PlannerService plannerService,
			ExecutorManager executorManager, TestAgentService testAgentService,
			AuditService auditService) {
		this.taskCenterService = taskCenterService;
		this.modelRouterService = modelRouterService;
		this.plannerService = plannerService;
		this.executorManager = executorManager;
		this.testAgentService = testAgentService;
		this.auditService = auditService;
	}

	/**
	 * Initial collaboration rules: selects the responsible agent for a task
	 * type. Unknown types fall back to Hermes.
	 */
	public String selectAgent(TaskType taskType) {
		return switch (taskType == null ? TaskType.GENERAL : taskType) {
			case TASK_ANALYSIS -> HERMES_AGENT;
			case CODE_GENERATION -> CODEX_AGENT;
			case BROWSER_TEST -> OPENCLAW_AGENT;
			case TEST_VERIFY -> TESTAGENT_AGENT;
			default -> HERMES_AGENT;
		};
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
			selectAgent(type), 0, null, "PENDING", "Agent collaboration plan created",
			Map.of("taskType", type.name(), "model", model.model()));
		execute(steps, task, model);
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
		List<String> chain = switch (taskType == null ? TaskType.GENERAL : taskType) {
			case TASK_ANALYSIS ->
				List.of(HERMES_AGENT, CODEX_AGENT, OPENCLAW_AGENT, TESTAGENT_AGENT);
			case CODE_GENERATION -> List.of(CODEX_AGENT, OPENCLAW_AGENT, TESTAGENT_AGENT);
			case BROWSER_TEST -> List.of(OPENCLAW_AGENT, TESTAGENT_AGENT);
			case TEST_VERIFY -> List.of(TESTAGENT_AGENT);
			default -> List.of(HERMES_AGENT, CODEX_AGENT, OPENCLAW_AGENT, TESTAGENT_AGENT);
		};
		List<AgentExecutionPlan> steps = new ArrayList<>();
		int stepNumber = 1;
		for (String agentId : chain) {
			steps.add(new AgentExecutionPlan(planId, taskId, agentId, stepNumber++));
		}
		return steps;
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
		try {
			String result = switch (step.getAgentId()) {
				case HERMES_AGENT -> runHermes(task, model);
				case CODEX_AGENT -> runCodex(task);
				case OPENCLAW_AGENT -> runOpenClaw(task);
				case TESTAGENT_AGENT -> runTestAgent(task);
				default -> throw new IllegalStateException(
					"Unsupported agent: " + step.getAgentId());
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

	private String runHermes(TaskRecord task, ResolvedModel model) {
		String goal = task.getDescription() == null || task.getDescription().isBlank()
			? task.getName() : task.getDescription();
		PlanningResult result = plannerService.createPlan(new PlanningRequest(task.getTaskId(),
			goal, HERMES_PLANNER, model == null ? null : model.model(), null, null, null, null));
		if (!result.success() || result.plan() == null) {
			throw new IllegalStateException("Planning failed: " + joinErrors(result.errors()));
		}
		return "Plan created: " + result.plan().id();
	}

	private String runCodex(TaskRecord task) {
		return summarize(executeAgent(CODEX_AGENT_NAME, task));
	}

	private String runOpenClaw(TaskRecord task) {
		return summarize(executeAgent(OPENCLAW_AGENT_NAME, task));
	}

	private ExecutionResult executeAgent(String agentName, TaskRecord task) {
		AgentExecutor executor = executorManager.getExecutor(agentName);
		if (executor == null) {
			throw new IllegalStateException("Executor not found for agent: " + agentName);
		}
		ExecutionContext context = new ExecutionContext();
		context.setTaskId(task.getTaskId());
		context.setTaskName(task.getName());
		context.setProjectId(task.getProjectId());
		context.setDescription(task.getDescription());
		context.setInput(task.getDescription() == null || task.getDescription().isBlank()
			? task.getName() : task.getDescription());
		context.setAgentName(agentName);
		return executor.execute(context);
	}

	private String runTestAgent(TaskRecord task) {
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
