package com.aidevos.orchestrator.orchestrator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.memory.MemoryContext;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.search.MemoryMatch;
import com.aidevos.orchestrator.modelrouter.TaskType;
import com.aidevos.orchestrator.optimization.OptimizationRecord;
import com.aidevos.orchestrator.optimization.OptimizationService;
import com.aidevos.orchestrator.optimization.OptimizationType;
import com.aidevos.orchestrator.orchestration.ExecutionGraph;
import com.aidevos.orchestrator.orchestration.ExecutionGraphBuilder;
import com.aidevos.orchestrator.planner.Plan;
import com.aidevos.orchestrator.planner.PlanningService;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.runtime.AgentSession;
import com.aidevos.orchestrator.runtime.AgentSessionStatus;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Autonomous orchestrator: manages the task pool and the priority queue,
 * schedules the next task, auto-selects its agents, plans a dynamic graph
 * from the task type / memory / optimization recommendations and runs it
 * through the existing AgentRuntimeService. Reuses Runtime, ExecutionGraph,
 * AgentSelector, Memory, Optimization and Observability; it never touches the
 * Scheduler / Worker / ExecutionEngine chain.
 */
@Service
public class OrchestratorService {

	private final TaskQueueRepository queueRepository;
	private final TaskPool pool;
	private final AuditService auditService;
	private final TaskCenterService taskCenterService;
	private final AgentAutoSelectionService autoSelectionService;
	private final OptimizationService optimizationService;
	private final AgentRuntimeService runtimeService;
	private final ExecutionGraphBuilder graphBuilder;
	private final MemoryService memoryService;
	private final PlanningService planningService;
	private boolean started;

	public OrchestratorService(TaskQueueRepository queueRepository, AuditService auditService,
			TaskCenterService taskCenterService, AgentAutoSelectionService autoSelectionService,
			OptimizationService optimizationService, AgentRuntimeService runtimeService,
			ExecutionGraphBuilder graphBuilder, MemoryService memoryService) {
		this(queueRepository, auditService, taskCenterService, autoSelectionService,
			optimizationService, runtimeService, graphBuilder, memoryService, null);
	}

	@Autowired
	public OrchestratorService(TaskQueueRepository queueRepository, AuditService auditService,
			TaskCenterService taskCenterService, AgentAutoSelectionService autoSelectionService,
			OptimizationService optimizationService, AgentRuntimeService runtimeService,
			ExecutionGraphBuilder graphBuilder, MemoryService memoryService,
			PlanningService planningService) {
		this.queueRepository = queueRepository;
		this.auditService = auditService;
		this.taskCenterService = taskCenterService;
		this.autoSelectionService = autoSelectionService;
		this.optimizationService = optimizationService;
		this.runtimeService = runtimeService;
		this.graphBuilder = graphBuilder;
		this.memoryService = memoryService;
		this.planningService = planningService;
		this.pool = new TaskPool("pool-" + UUID.randomUUID());
	}

	/**
	 * Submits a task to the pool and the priority queue. The orchestrator
	 * starts on the first submission (ORCHESTRATOR_STARTED) and the task is
	 * audited as TASK_QUEUED.
	 */
	public OrchestrationTask submitTask(String taskId, String taskType,
			TaskPriority priority, List<String> requiredAgents) {
		if (taskId == null || taskId.isBlank()) {
			throw new IllegalArgumentException("Task id is required");
		}
		if (pool.contains(taskId)) {
			throw new IllegalStateException("Task already submitted: " + taskId);
		}
		ensureStarted();
		TaskPriority resolved = priority == null ? TaskPriority.NORMAL : priority;
		OrchestrationTask task = new OrchestrationTask(taskId, taskType, resolved,
			requiredAgents);
		queueRepository.add(task);
		pool.addTask(task);
		pool.markRunning();
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("priority", resolved.name());
		metadata.put("taskType", task.getTaskType());
		metadata.put("poolId", pool.getPoolId());
		auditService.orchestratorEvent(EventType.TASK_QUEUED, taskId, taskId, null,
			OrchestrationTaskStatus.QUEUED.name(), "Task queued with priority "
				+ resolved.name(), Map.copyOf(metadata));
		return task;
	}

	/**
	 * Schedules the next task: the highest priority queued task (FIFO within
	 * the priority) is dequeued and marked RUNNING.
	 */
	public Optional<OrchestrationTask> scheduleNextTask() {
		OrchestrationTask next = queueRepository.next();
		if (next == null) {
			return Optional.empty();
		}
		queueRepository.remove(next.getTaskId());
		next.markRunning();
		return Optional.of(next);
	}

	/**
	 * Re-orders the queue by priority (CRITICAL first, FIFO within it) and
	 * audits TASK_PRIORITIZED. The queue already serves by priority; this
	 * makes the order explicit for the pool view.
	 */
	public List<OrchestrationTask> prioritizeTasks() {
		List<OrchestrationTask> sorted = sortedByPriority(queueRepository.list());
		for (OrchestrationTask task : queueRepository.list()) {
			queueRepository.remove(task.getTaskId());
		}
		sorted.forEach(queueRepository::add);
		auditService.orchestratorEvent(EventType.TASK_PRIORITIZED, pool.getPoolId(), null,
			null, null, "Task queue prioritized",
			Map.of("queueSize", sorted.size(), "poolId", pool.getPoolId()));
		return sorted;
	}

	/**
	 * Auto-selects the agent flow for a task through AgentAutoSelectionService
	 * (AgentSelector + AgentScore + MemoryContext) and stores the assigned
	 * agents on the orchestration task.
	 */
	public List<String> assignAgents(String taskId) {
		OrchestrationTask task = require(taskId);
		List<OptimizationRecord> recommendations = recommendations(taskId);
		MemoryContext memory = buildMemoryContext(taskCenterService.getTask(taskId)
			.orElse(null), recommendations);
		List<AgentType> selected = autoSelectionService.selectAgents(taskId,
			task.getTaskType(), memory);
		List<String> names = selected.stream().map(AgentType::name).toList();
		task.setAssignedAgents(names);
		return names;
	}

	/**
	 * Starts an orchestrated task: marks it RUNNING, auto-assigns agents,
	 * plans the dynamic graph (task type + memory + optimization
	 * recommendations), audits DYNAMIC_GRAPH_CREATED and runs it through the
	 * runtime session. The orchestration task finishes COMPLETED or FAILED
	 * from the session outcome.
	 */
	public OrchestrationTask startTask(String taskId) {
		OrchestrationTask task = require(taskId);
		if (task.getStatus() == OrchestrationTaskStatus.QUEUED) {
			queueRepository.remove(taskId);
			task.markRunning();
		}
		if (task.getStatus() != OrchestrationTaskStatus.RUNNING) {
			throw new IllegalStateException("Task cannot be started: "
				+ task.getStatus());
		}
		assignAgents(taskId);
		TaskRecord record = taskCenterService.getTask(taskId).orElse(null);
		List<OptimizationRecord> recommendations = recommendations(taskId);
		MemoryContext memory = buildMemoryContext(record, recommendations);
		ExecutionGraph graph;
		String planId = null;
		if (planningService != null) {
			Plan plan = planningService.createPlan(task);
			Plan evaluated = planningService.evaluatePlan(plan);
			Plan optimized = planningService.optimizePlan(evaluated);
			graph = planningService.generateGraph(optimized);
			planId = optimized.planId();
		}
		else {
			graph = graphBuilder.buildDynamic(record,
				TaskType.from(task.getTaskType()), memory, recommendations);
		}
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("graphId", graph.getGraphId());
		metadata.put("taskType", task.getTaskType());
		metadata.put("nodeCount", graph.getNodes().size());
		metadata.put("hasLoop", graph.hasLoop());
		metadata.put("recommendationCount", recommendations.size());
		if (planId != null) {
			metadata.put("planId", planId);
		}
		auditService.orchestratorEvent(EventType.DYNAMIC_GRAPH_CREATED,
			graph.getGraphId(), taskId, OrchestrationTaskStatus.QUEUED.name(),
			OrchestrationTaskStatus.RUNNING.name(),
			"Dynamic graph created for task " + taskId, Map.copyOf(metadata));

		AgentSession session = runtimeService.startSession(taskId, graph);
		if (session.getStatus() == AgentSessionStatus.COMPLETED) {
			task.markCompleted();
		}
		else if (session.getStatus() == AgentSessionStatus.FAILED) {
			task.markFailed("Session failed at node "
				+ (session.getCurrentNodeId() == null ? "" : session.getCurrentNodeId()));
		}
		else if (session.getStatus() == AgentSessionStatus.STOPPED) {
			task.markFailed("Session stopped");
		}
		updatePoolCompletion();
		return task;
	}

	/**
	 * Pauses a queued or running task and its running runtime session; the
	 * pool moves to PAUSED.
	 */
	public OrchestrationTask pauseTask(String taskId) {
		OrchestrationTask task = require(taskId);
		if (task.getStatus() != OrchestrationTaskStatus.RUNNING
			&& task.getStatus() != OrchestrationTaskStatus.QUEUED) {
			throw new IllegalStateException("Only RUNNING or QUEUED tasks can be "
				+ "paused: " + taskId);
		}
		task.markPaused();
		pool.markPaused();
		if (runtimeService != null) {
			runtimeService.sessionsForTask(taskId).stream()
				.filter(session -> session.getStatus() == AgentSessionStatus.RUNNING)
				.findFirst()
				.ifPresent(session -> runtimeService.pauseSession(session.getSessionId()));
		}
		return task;
	}

	public TaskPool getPool() {
		return pool;
	}

	public Optional<OrchestrationTask> getTask(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(pool.get(taskId));
	}

	public List<OrchestrationTask> listTasks() {
		return pool.getTasks();
	}

	private List<OptimizationRecord> recommendations(String taskId) {
		return optimizationService == null ? List.of()
			: optimizationService.getRecommendations(taskId);
	}

	private MemoryContext buildMemoryContext(TaskRecord record,
			List<OptimizationRecord> recommendations) {
		if (record == null) {
			return new MemoryContext();
		}
		String query = record.getDescription() == null || record.getDescription().isBlank()
			? record.getName() : record.getDescription();
		List<MemoryMatch> similar = memoryService == null ? List.of()
			: memoryService.findSimilarTasks(record.getProjectId(), query, 5);
		List<MemoryMatch> solutions = memoryService == null ? List.of()
			: memoryService.findSolutions(record.getProjectId(), query, 5);
		List<String> warnings = recommendations.stream()
			.filter(recordRecommendation -> recordRecommendation.getType()
				== OptimizationType.FAILURE_PATTERN)
			.map(OptimizationRecord::getRecommendation)
			.toList();
		return new MemoryContext(similar, solutions, warnings, List.of());
	}

	private List<OrchestrationTask> sortedByPriority(List<OrchestrationTask> tasks) {
		List<OrchestrationTask> sorted = new ArrayList<>(tasks);
		sorted.sort(Comparator.comparingInt((OrchestrationTask task) ->
				task.getPriority() == null ? 0 : task.getPriority().ordinal())
			.reversed()
			.thenComparing(OrchestrationTask::getCreatedAt));
		return sorted;
	}

	private void updatePoolCompletion() {
		boolean allDone = queueRepository.list().isEmpty() && !pool.getTasks().isEmpty()
			&& pool.getTasks().stream().allMatch(task ->
				task.getStatus() == OrchestrationTaskStatus.COMPLETED
					|| task.getStatus() == OrchestrationTaskStatus.FAILED);
		if (allDone) {
			pool.markCompleted();
		}
	}

	private void ensureStarted() {
		if (!started) {
			started = true;
			pool.markRunning();
			auditService.orchestratorEvent(EventType.ORCHESTRATOR_STARTED,
				pool.getPoolId(), null, TaskPoolStatus.CREATED.name(),
				TaskPoolStatus.RUNNING.name(), "Autonomous orchestrator started",
				Map.of("poolId", pool.getPoolId()));
		}
	}

	private OrchestrationTask require(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			throw new IllegalArgumentException("Task id is required");
		}
		OrchestrationTask task = pool.get(taskId);
		if (task == null) {
			throw new IllegalArgumentException("Task not in pool: " + taskId);
		}
		return task;
	}
}
