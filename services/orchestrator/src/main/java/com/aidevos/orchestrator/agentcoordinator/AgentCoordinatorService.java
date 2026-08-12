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
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.memory.MemoryContext;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.MemoryType;
import com.aidevos.orchestrator.memory.search.MemoryMatch;
import com.aidevos.orchestrator.memory.search.MemoryQuery;
import com.aidevos.orchestrator.memory.search.MemorySearchService;
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
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.git.GitDiff;
import com.aidevos.orchestrator.workspace.git.GitStatus;
import com.aidevos.orchestrator.orchestration.AgentExecutionContext;
import com.aidevos.orchestrator.orchestration.ExecutionGraph;
import com.aidevos.orchestrator.orchestration.ExecutionGraphBuilder;
import com.aidevos.orchestrator.orchestration.ExecutionGraphExecutor;
import com.aidevos.orchestrator.orchestration.ExecutionNode;
import com.aidevos.orchestrator.orchestration.ExecutionNodeStatus;
import org.springframework.beans.factory.annotation.Autowired;
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
	private final Map<String, ExecutionGraph> graphs = new ConcurrentHashMap<>();
	private final TaskCenterService taskCenterService;
	private final ModelRouterService modelRouterService;
	private final PlannerService plannerService;
	private final ExecutorManager executorManager;
	private final TestAgentService testAgentService;
	private final AuditService auditService;
	private final AgentCapabilityResolver capabilityResolver;
	private final MemoryService memoryService;
	private final ExecutionRecordManager executionRecordManager;
	private WorkspaceService workspaceService;
	private final ChangeService changeService;
	private final ExecutionGraphBuilder graphBuilder;
	private final ExecutionGraphExecutor graphExecutor;
	private final MemorySearchService memorySearchService;

	public AgentCoordinatorService(TaskCenterService taskCenterService,
			ModelRouterService modelRouterService, PlannerService plannerService,
			ExecutorManager executorManager, TestAgentService testAgentService,
			AuditService auditService, AgentCapabilityResolver capabilityResolver,
			MemoryService memoryService, ExecutionRecordManager executionRecordManager) {
		this(taskCenterService, modelRouterService, plannerService, executorManager,
			testAgentService, auditService, capabilityResolver, memoryService,
			executionRecordManager, null, null);
	}

	public AgentCoordinatorService(TaskCenterService taskCenterService,
			ModelRouterService modelRouterService, PlannerService plannerService,
			ExecutorManager executorManager, TestAgentService testAgentService,
			AuditService auditService, AgentCapabilityResolver capabilityResolver,
			MemoryService memoryService, ExecutionRecordManager executionRecordManager,
			WorkspaceService workspaceService) {
		this(taskCenterService, modelRouterService, plannerService, executorManager,
			testAgentService, auditService, capabilityResolver, memoryService,
			executionRecordManager, workspaceService, null);
	}

	public AgentCoordinatorService(TaskCenterService taskCenterService,
			ModelRouterService modelRouterService, PlannerService plannerService,
			ExecutorManager executorManager, TestAgentService testAgentService,
			AuditService auditService, AgentCapabilityResolver capabilityResolver,
			MemoryService memoryService, ExecutionRecordManager executionRecordManager,
			WorkspaceService workspaceService, ChangeService changeService) {
		this(taskCenterService, modelRouterService, plannerService, executorManager,
			testAgentService, auditService, capabilityResolver, memoryService,
			executionRecordManager, workspaceService, changeService, null, null);
	}

	public AgentCoordinatorService(TaskCenterService taskCenterService,
			ModelRouterService modelRouterService, PlannerService plannerService,
			ExecutorManager executorManager, TestAgentService testAgentService,
			AuditService auditService, AgentCapabilityResolver capabilityResolver,
			MemoryService memoryService, ExecutionRecordManager executionRecordManager,
			WorkspaceService workspaceService, ChangeService changeService,
			ExecutionGraphBuilder graphBuilder, ExecutionGraphExecutor graphExecutor) {
		this(taskCenterService, modelRouterService, plannerService, executorManager,
			testAgentService, auditService, capabilityResolver, memoryService,
			executionRecordManager, workspaceService, changeService, graphBuilder,
			graphExecutor, null);
	}

	@Autowired
	public AgentCoordinatorService(TaskCenterService taskCenterService,
			ModelRouterService modelRouterService, PlannerService plannerService,
			ExecutorManager executorManager, TestAgentService testAgentService,
			AuditService auditService, AgentCapabilityResolver capabilityResolver,
			MemoryService memoryService, ExecutionRecordManager executionRecordManager,
			WorkspaceService workspaceService, ChangeService changeService,
			ExecutionGraphBuilder graphBuilder, ExecutionGraphExecutor graphExecutor,
			MemorySearchService memorySearchService) {
		this.taskCenterService = taskCenterService;
		this.modelRouterService = modelRouterService;
		this.plannerService = plannerService;
		this.executorManager = executorManager;
		this.testAgentService = testAgentService;
		this.auditService = auditService;
		this.capabilityResolver = capabilityResolver;
		this.memoryService = memoryService;
		this.executionRecordManager = executionRecordManager;
		this.workspaceService = workspaceService;
		this.changeService = changeService;
		this.graphBuilder = graphBuilder;
		this.graphExecutor = graphExecutor;
		this.memorySearchService = memorySearchService;
	}

	/**
	 * Associates the workspace service used to resolve workspace paths for
	 * coding steps. Injected through the constructor in production; tests that
	 * build the coordinator manually can wire it here.
	 */
	public void setWorkspaceService(WorkspaceService workspaceService) {
		this.workspaceService = workspaceService;
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
		List<AgentExecutionPlan> steps = buildSteps(planId, taskId, task.getProjectId(),
			task.getWorkspaceId(), task.getExecutionMode(), type);
		plans.put(taskId, steps);
		auditService.agentPlanEvent(EventType.AGENT_PLAN_CREATED, planId, taskId,
			steps.getFirst().getAgentId(), 0, null, "PENDING",
			"Agent collaboration plan created",
			Map.of("taskType", type.name(), "model", model.model()));
		execute(steps, task, model);
		finalizeOutcome(steps, task);
		return List.copyOf(steps);
	}

	/**
	 * Unified graph-based execution entry: builds an ExecutionGraph for the
	 * task, executes it in topology order through the graph executor and
	 * writes back the closed-loop outcome (task status + project memory).
	 * The legacy collaboration-plan path stays available and unchanged.
	 */
	public ExecutionGraph executeGraph(String taskId) {
		return executeGraph(taskId, TaskType.GENERAL);
	}

	public ExecutionGraph executeGraph(String taskId, TaskType taskType) {
		TaskRecord task = taskCenterService.getTask(taskId)
			.orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
		if (graphBuilder == null || graphExecutor == null) {
			throw new IllegalStateException(
				"Execution graph components are not configured");
		}
		TaskType type = taskType == null ? TaskType.GENERAL : taskType;
		MemoryContext memoryHints = searchMemory(task, type);
		ExecutionGraph graph = graphBuilder.build(task, type, memoryHints);
		graphs.put(taskId, graph);
		auditService.graphEvent(EventType.GRAPH_CREATED, graph.getGraphId(), taskId, null,
			null, "CREATED", "Execution graph created",
			Map.of("graphId", graph.getGraphId(), "taskType", type.name(),
				"category", graphCategory(type)));
		AgentExecutionContext context = new AgentExecutionContext();
		context.setTaskId(taskId);
		context.setTask(task);
		context.setWorkspaceId(task.getWorkspaceId());
		context.setWorkspacePath(resolveWorkspacePath(task.getWorkspaceId()));
		context.setExecutionMode(task.getExecutionMode());
		context.setGraphId(graph.getGraphId());
		context.setMemoryHints(memoryHints);
		context.setInput(task.getDescription() == null || task.getDescription().isBlank()
			? task.getName() : task.getDescription());
		if (memoryHints != null && !memoryHints.isEmpty()) {
			auditService.memoryEvent(EventType.MEMORY_APPLIED, taskId,
				description(task), memoryHints.getSimilarTasks().size()
					+ memoryHints.getSolutions().size(),
				java.util.List.of(), "Memory hints applied to execution graph",
				Map.of("graphId", graph.getGraphId()));
		}
		graphExecutor.execute(graph, context);
		finalizeGraphOutcome(graph, task);
		return graph;
	}

	/**
	 * Retrieves historical experience (similar tasks, known issues and
	 * recommended solutions) before the graph runs so agents can reuse it.
	 * Audits MEMORY_SEARCHED / MEMORY_MATCHED with the taskId so the search
	 * appears on the task timeline.
	 */
	private MemoryContext searchMemory(TaskRecord task, TaskType taskType) {
		if (memorySearchService == null) {
			return null;
		}
		String query = description(task);
		MemoryQuery memoryQuery = new MemoryQuery(query, taskType == null ? null
			: taskType.name(), null, task.getProjectId(), 10);
		List<MemoryMatch> matches = memorySearchService.search(memoryQuery);
		auditService.memoryEvent(EventType.MEMORY_SEARCHED, task.getTaskId(), query,
			matches.size(), java.util.List.of(),
			"Memory search executed before agent execution", Map.of());
		if (matches.isEmpty()) {
			return new MemoryContext();
		}
		auditService.memoryEvent(EventType.MEMORY_MATCHED, task.getTaskId(), query,
			matches.size(), matches.stream().map(MemoryMatch::memoryId).toList(),
			"Memory records matched for task", Map.of());
		MemoryContext context = new MemoryContext();
		context.setSimilarTasks(matches.stream()
			.filter(match -> match.type() == MemoryType.HISTORY_TASK).toList());
		context.setSolutions(matches.stream()
			.filter(match -> match.type() == MemoryType.BUG_RECORD
				|| match.type() == MemoryType.AGENT_EXPERIENCE).toList());
		context.setWarnings(matches.stream()
			.filter(match -> match.type() == MemoryType.BUG_RECORD
				&& !Boolean.TRUE.equals(match.metadata().get("resolved")))
			.map(match -> match.summary())
			.limit(3).toList());
		context.setRecommendations(matches.stream()
			.map(MemoryMatch::solution)
			.filter(solution -> solution != null && !solution.isBlank())
			.limit(5).toList());
		return context;
	}

	private String description(TaskRecord task) {
		return task == null || task.getDescription() == null || task.getDescription().isBlank()
			? task == null ? "" : task.getName() : task.getDescription();
	}

	public Optional<ExecutionGraph> getGraph(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(graphs.get(taskId));
	}

	private String graphCategory(TaskType taskType) {
		return switch (taskType == null ? TaskType.GENERAL : taskType) {
			case BROWSER_TEST -> "BROWSER_TASK";
			case TEST_VERIFY -> "TEST_TASK";
			default -> "CODE_TASK";
		};
	}

	/**
	 * Writes back the graph outcome: task status and project memory
	 * (HISTORY_TASK on success, BUG_RECORD on failure). Mirrors the legacy
	 * collaboration-plan finalization.
	 */
	private void finalizeGraphOutcome(ExecutionGraph graph, TaskRecord task) {
		boolean succeeded = graph.getNodes().stream()
			.allMatch(node -> node.getStatus() == ExecutionNodeStatus.COMPLETED);
		if (succeeded) {
			String from = task.getStatus().name();
			task.markCompleted();
			auditService.taskEvent(EventType.USER_OPERATION, task.getTaskId(), from,
				TaskStatus.COMPLETED.name(), "Task completed (graph)",
				Map.of("graphId", graph.getGraphId(), "nodes", graph.getNodes().size()));
			saveGraphHistory(task, graph);
		}
		else {
			String from = task.getStatus().name();
			String error = graph.getNodes().stream()
				.filter(node -> node.getStatus() == ExecutionNodeStatus.FAILED)
				.map(ExecutionNode::getResult)
				.findFirst()
				.orElse("Execution graph failed");
			task.markFailed(error);
			auditService.taskEvent(EventType.USER_OPERATION, task.getTaskId(), from,
				TaskStatus.FAILED.name(), "Task failed (graph): " + error,
				Map.of("graphId", graph.getGraphId()));
			saveGraphBugRecord(task, error);
		}
		taskCenterService.saveTask(task);
	}

	private void saveGraphHistory(TaskRecord task, ExecutionGraph graph) {
		try {
			MemoryRecord record = new MemoryRecord();
			record.setProjectId(task.getProjectId());
			record.setType(MemoryType.HISTORY_TASK);
			record.setKey("history:task:" + task.getTaskId());
			record.setContent(graphHistoryContent(task, graph));
			memoryService.create(record);
		}
		catch (RuntimeException exception) {
			// Memory must not break the task flow; the failure is already audited.
		}
	}

	private void saveGraphBugRecord(TaskRecord task, String error) {
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

	private String graphHistoryContent(TaskRecord task, ExecutionGraph graph) {
		StringBuilder builder = new StringBuilder();
		builder.append("任务: ").append(task.getTaskId()).append(System.lineSeparator())
			.append("名称: ").append(task.getName()).append(System.lineSeparator())
			.append("项目: ").append(task.getProjectId()).append(System.lineSeparator())
			.append("执行图: ").append(graph.getGraphId()).append(System.lineSeparator())
			.append("节点:").append(System.lineSeparator());
		for (ExecutionNode node : graph.getNodes()) {
			builder.append("  ").append(node.getNodeId()).append(" -> ")
				.append(node.getAgentType()).append(" [").append(node.getStatus()).append("]")
				.append(System.lineSeparator());
		}
		return builder.toString();
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

	private List<AgentExecutionPlan> buildSteps(String planId, String taskId,
			String projectId, String workspaceId,
			com.aidevos.orchestrator.taskcenter.ExecutionMode executionMode,
			TaskType taskType) {
		List<AgentExecutionPlan> steps = new ArrayList<>();
		int stepNumber = 1;
		for (String capability : capabilitiesFor(taskType)) {
			AgentDefinition agent = capabilityResolver.resolveAgent(capability)
				.orElseThrow(() -> new IllegalStateException(
					"No agent found for capability: " + capability));
			steps.add(new AgentExecutionPlan(planId, taskId, agent.getName(), stepNumber++,
				capability, workspaceId, projectId, executionMode));
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
				case CODING_CAPABILITY -> runCoding(task, step.getAgentId(),
					step.getWorkspaceId());
				case BROWSER_CAPABILITY -> runBrowser(task, step.getAgentId(),
					step.getWorkspaceId());
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
			goal, HERMES_PLANNER, model == null ? null : model.model(), null, null, null,
			planningMetadata(task, resolveWorkspacePath(task.getWorkspaceId()))));
		if (!result.success() || result.plan() == null) {
			throw new IllegalStateException("Planning failed: " + joinErrors(result.errors()));
		}
		return "Plan created: " + result.plan().id();
	}

	private String runCoding(TaskRecord task, String agentName, String workspaceId) {
		String executionId = "exec-" + UUID.randomUUID();
		String workspacePath = resolveWorkspacePath(workspaceId);
		auditService.codexExecutionEvent(EventType.CODEX_EXEC_STARTED, task.getTaskId(),
			executionId, workspacePath, "Codex execution started",
			Map.of("agent", agentName));
		try {
			ExecutionResult result = executeAgent(agentName, task, workspaceId, executionId);
			if (!result.isSuccess()) {
				throw new IllegalStateException(message(result));
			}
			auditService.codexExecutionEvent(EventType.CODEX_EXEC_COMPLETED, task.getTaskId(),
				executionId, workspacePath, "Codex execution completed",
				Map.of("workspace", workspacePath == null ? "" : workspacePath));
			recordChange(task, workspaceId, executionId);
			return summarize(result);
		}
		catch (RuntimeException exception) {
			auditService.codexExecutionEvent(EventType.CODEX_EXEC_FAILED, task.getTaskId(),
				executionId, workspacePath, "Codex execution failed: "
					+ errorMessage(exception), Map.of());
			throw exception;
		}
	}

	/**
	 * Captures a change set for the coding step after a successful codex run.
	 * Read-only git snapshot; change tracking must never break the agent flow.
	 */
	private void recordChange(TaskRecord task, String workspaceId, String executionId) {
		if (changeService == null || workspaceId == null || workspaceId.isBlank()
				|| task.getExecutionMode() == com.aidevos.orchestrator.taskcenter.ExecutionMode.READ_ONLY) {
			return;
		}
		try {
			changeService.createChange(task.getTaskId(), workspaceId, task.getProjectId(),
				executionId);
		}
		catch (RuntimeException exception) {
			// Change tracking must not break the agent flow.
		}
	}

	private String runBrowser(TaskRecord task, String agentName, String workspaceId) {
		String executionId = "exec-" + UUID.randomUUID();
		return summarize(executeAgent(agentName, task, workspaceId, executionId));
	}

	private ExecutionResult executeAgent(String agentName, TaskRecord task, String workspaceId,
			String executionId) {
		AgentExecutor executor = executorManager.getExecutor(agentName);
		if (executor == null) {
			throw new IllegalStateException("Executor not found for agent: " + agentName);
		}
		String workspacePath = resolveWorkspacePath(workspaceId);
		ExecutionContext context = new ExecutionContext();
		context.setExecutionId(executionId);
		context.setTaskId(task.getTaskId());
		context.setTaskName(task.getName());
		context.setProjectId(task.getProjectId());
		context.getMetadata().put("workspaceId", workspaceId);
		context.getMetadata().put("executionMode", task.getExecutionMode().name());
		context.getParameters().put("executionMode", task.getExecutionMode().name());
		if (task.getExecutionMode() == com.aidevos.orchestrator.taskcenter.ExecutionMode.READ_ONLY) {
			context.getParameters().put("sandbox", "read-only");
		}
		context.setDescription(task.getDescription());
		context.setInput(task.getDescription() == null || task.getDescription().isBlank()
			? task.getName() : task.getDescription());
		context.setAgentName(agentName);
		if (workspacePath != null) {
			context.setWorkspace(workspacePath);
			context.getParameters().put("workspace", workspacePath);
		}
		ExecutionResult result = executor.execute(context);
		saveExecutionRecord(task, agentName, executionId, result, workspaceId, workspacePath);
		return result;
	}

	private Map<String, Object> planningMetadata(TaskRecord task, String workspacePath) {
		return Map.of("projectId", task.getProjectId(), "workspaceId",
			task.getWorkspaceId() == null ? "" : task.getWorkspaceId(),
			"workspacePath", workspacePath == null ? "" : workspacePath,
			"executionMode", task.getExecutionMode().name());
	}

	private String resolveWorkspacePath(String workspaceId) {
		if (workspaceId == null || workspaceId.isBlank() || workspaceService == null) {
			return null;
		}
		return workspaceService.getWorkspace(workspaceId)
			.map(Workspace::getPath)
			.orElse(null);
	}

	private String message(ExecutionResult result) {
		return result.getMessage() == null || result.getMessage().isBlank()
			? "Agent execution failed" : result.getMessage();
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
		taskCenterService.saveTask(task);
	}

	private void saveExecutionRecord(TaskRecord task, String agentName, String executionId,
			ExecutionResult result, String workspaceId, String workspacePath) {
		try {
			ExecutionRecord record = new ExecutionRecord();
			record.setId(executionId);
			record.setExecutionId(executionId);
			record.setTaskId(task.getTaskId());
			record.setAgentName(agentName);
			record.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
			record.setMessage(result.getMessage());
			record.setOutput(result.getOutput());
			record.setWorkspace(metadataString(result, "workspace", workspacePath));
			record.setBranch(metadataString(result, "branch", null));
			Object exitCode = result.getMetadata().get("exitCode");
			if (exitCode instanceof Number number) {
				record.setExitCode(number.intValue());
			}
			Object threadId = result.getMetadata().get("codexThreadId");
			if (threadId instanceof String value) {
				record.setCodexThreadId(value);
			}
			captureGitState(record, workspaceId);
			Instant now = Instant.now();
			record.setStartedAt(now);
			record.setCompletedAt(now);
			executionRecordManager.save(record);
		}
		catch (RuntimeException exception) {
			// Execution record persistence must not break the agent flow.
		}
	}

	private void captureGitState(ExecutionRecord record, String workspaceId) {
		if (workspaceId == null || workspaceId.isBlank() || workspaceService == null) {
			return;
		}
		try {
			GitStatus gitStatus = workspaceService.checkGitStatus(workspaceId);
			record.setGitStatus("branch=" + nullToEmpty(gitStatus.getBranch())
				+ " modified=" + gitStatus.getModified()
				+ " added=" + gitStatus.getAdded()
				+ " deleted=" + gitStatus.getDeleted());
			GitDiff gitDiff = workspaceService.getGitDiff(workspaceId);
			record.setGitDiffStat(nullToEmpty(gitDiff.getStat()));
		}
		catch (RuntimeException exception) {
			// Git inspection must not break the agent flow.
		}
	}

	private String metadataString(ExecutionResult result, String key, String defaultValue) {
		Object value = result.getMetadata().get(key);
		return value instanceof String text ? text : defaultValue;
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
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
