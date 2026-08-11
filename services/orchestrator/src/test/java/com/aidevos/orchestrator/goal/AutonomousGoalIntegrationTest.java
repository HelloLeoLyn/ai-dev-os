package com.aidevos.orchestrator.goal;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.aidevos.orchestrator.agent.AgentSelector;
import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.collaboration.AgentCollaborationService;
import com.aidevos.orchestrator.collaboration.InMemoryAgentMessageRepository;
import com.aidevos.orchestrator.collaboration.InMemoryAgentTeamRepository;
import com.aidevos.orchestrator.human.InMemoryHumanApprovalRepository;
import com.aidevos.orchestrator.human.InMemoryHumanFeedbackRepository;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.memory.InMemoryMemoryRepository;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.metrics.agent.AgentMetrics;
import com.aidevos.orchestrator.metrics.agent.AgentMetricsService;
import com.aidevos.orchestrator.observability.ExecutionTraceService;
import com.aidevos.orchestrator.observability.InMemoryTraceRepository;
import com.aidevos.orchestrator.observability.usage.UsageService;
import com.aidevos.orchestrator.observability.usage.UsageSummary;
import com.aidevos.orchestrator.optimization.AgentOptimizationService;
import com.aidevos.orchestrator.optimization.InMemoryOptimizationRepository;
import com.aidevos.orchestrator.optimization.OptimizationService;
import com.aidevos.orchestrator.orchestration.AgentExecutionContext;
import com.aidevos.orchestrator.orchestration.AgentExecutionResult;
import com.aidevos.orchestrator.orchestration.AgentExecutor;
import com.aidevos.orchestrator.orchestration.ExecutionGraphBuilder;
import com.aidevos.orchestrator.orchestration.ExecutionGraphExecutor;
import com.aidevos.orchestrator.orchestration.ExecutionNodeStatus;
import com.aidevos.orchestrator.orchestrator.AgentAutoSelectionService;
import com.aidevos.orchestrator.orchestrator.InMemoryTaskQueueRepository;
import com.aidevos.orchestrator.orchestrator.OrchestrationTask;
import com.aidevos.orchestrator.orchestrator.OrchestrationTaskStatus;
import com.aidevos.orchestrator.orchestrator.OrchestratorService;
import com.aidevos.orchestrator.orchestrator.TaskPoolStatus;
import com.aidevos.orchestrator.planner.PlanningService;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.runtime.AgentSession;
import com.aidevos.orchestrator.runtime.AgentSessionStatus;
import com.aidevos.orchestrator.runtime.InMemoryAgentSessionRepository;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Autonomous goal integration verification: a goal is planned, decomposed
 * into milestones, generated into orchestration tasks, executed through the
 * runtime sessions and evaluated back into goal progress (Goal -> Milestone
 * -> Task -> Runtime -> Result -> Progress). Completing every generated task
 * marks the goal COMPLETED; a failing node fails the goal.
 */
class AutonomousGoalIntegrationTest {

	private final InMemoryGoalRepository goalRepository = new InMemoryGoalRepository();
	private final InMemoryGoalMilestoneRepository milestoneRepository =
		new InMemoryGoalMilestoneRepository();
	private final InMemoryGoalTaskRepository goalTaskRepository =
		new InMemoryGoalTaskRepository();
	private final InMemoryTaskQueueRepository queueRepository =
		new InMemoryTaskQueueRepository();
	private final InMemoryOptimizationRepository optimizationRepository =
		new InMemoryOptimizationRepository();
	private final InMemoryAgentSessionRepository sessionRepository =
		new InMemoryAgentSessionRepository();
	private final InMemoryAgentTeamRepository teamRepository =
		new InMemoryAgentTeamRepository();
	private final InMemoryAgentMessageRepository messageRepository =
		new InMemoryAgentMessageRepository();
	private final InMemoryHumanApprovalRepository approvalRepository =
		new InMemoryHumanApprovalRepository();
	private final InMemoryHumanFeedbackRepository feedbackRepository =
		new InMemoryHumanFeedbackRepository();
	private final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
	private final AuditService auditService = new AuditService(auditRepository);
	private final InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
	private final MemoryService memoryService = new MemoryService(memoryRepository);
	private final ExecutionTraceService traceService =
		new ExecutionTraceService(new InMemoryTraceRepository());
	private final ExecutionGraphBuilder graphBuilder = new ExecutionGraphBuilder();
	private final TaskCenterService taskCenterService = mock(TaskCenterService.class);
	private final AgentMetricsService metricsService = mock(AgentMetricsService.class);
	private final UsageService usageService = mock(UsageService.class);
	private final MutableRuntimeProvider runtimeProvider = new MutableRuntimeProvider();
	private final AgentCollaborationService collaborationService =
		new AgentCollaborationService(teamRepository, messageRepository, auditService,
			memoryService, taskCenterService);
	private final AgentOptimizationService agentOptimizationService =
		new AgentOptimizationService(metricsService, traceService, usageService,
			teamRepository, messageRepository, approvalRepository, auditService);
	private final OptimizationService optimizationService = new OptimizationService(
		optimizationRepository, auditService, memoryService, taskCenterService,
		agentOptimizationService);
	private final PlanningService planningService = new PlanningService(taskCenterService,
		memoryService, optimizationService, agentOptimizationService,
		new AgentSelector(mock(AgentManager.class)), graphBuilder, auditService);
	private AgentRuntimeService runtime;
	private OrchestratorService orchestratorService;
	private GoalManagementService goalService;

	@BeforeEach
	void setUp() {
		when(usageService.getAgentUsage(anyString())).thenReturn(UsageSummary.empty());
		when(metricsService.listAgentMetrics()).thenReturn(List.of(
			metrics("HERMES", 10, 8, 2, 900),
			metrics("CODEX", 10, 9, 1, 1200),
			metrics("TEST_AGENT", 10, 7, 3, 1500)));
	}

	@Test
	void completedGoalRunsTasksThroughRuntimeAndEvaluatesProgress() {
		wire(success(AgentType.HERMES), success(AgentType.CODEX),
			success(AgentType.TEST_AGENT));
		Goal goal = goalService.createGoal("project-x", "开发用户管理模块", "开发用户管理模块",
			GoalPriority.HIGH);
		goalService.analyzeGoal(goal.getGoalId());
		goalService.decomposeGoal(goal.getGoalId());
		List<GoalTask> generated = goalService.generateTasks(goal.getGoalId());
		stubTaskRecords(generated);

		for (GoalTask link : generated) {
			OrchestrationTask finished = orchestratorService.startTask(link.getTaskId());
			assertEquals(OrchestrationTaskStatus.COMPLETED, finished.getStatus());
		}

		GoalEvaluation evaluation = goalService.evaluateCompletion(goal.getGoalId());

		assertEquals(GoalStatus.COMPLETED, goal.getStatus());
		assertEquals(100.0, goal.getProgress());
		assertEquals(4, evaluation.completedTasks());
		assertEquals(4, evaluation.totalTasks());
		assertEquals(100.0, evaluation.progress());
		assertEquals(0, evaluation.remainingWork());
		assertEquals(1.0, evaluation.confidence());
		assertEquals(TaskPoolStatus.COMPLETED, orchestratorService.getPool().getStatus());
		for (GoalTask link : generated) {
			AgentSession session = runtime.sessionsForTask(link.getTaskId()).get(0);
			assertEquals(AgentSessionStatus.COMPLETED, session.getStatus());
		}
		assertEvent(EventType.GOAL_CREATED);
		assertEvent(EventType.GOAL_PLANNING_STARTED);
		assertEvent(EventType.GOAL_DECOMPOSED);
		assertEvent(EventType.GOAL_TASK_CREATED);
		assertEvent(EventType.GOAL_PROGRESS_UPDATED);
		assertEvent(EventType.GOAL_COMPLETED);
	}

	@Test
	void failingNodeFailsTheGoal() {
		wire(success(AgentType.HERMES), failure(AgentType.CODEX, "compile error"),
			success(AgentType.TEST_AGENT));
		Goal goal = goalService.createGoal("project-x", "开发用户管理模块", "开发用户管理模块",
			GoalPriority.NORMAL);
		goalService.analyzeGoal(goal.getGoalId());
		goalService.decomposeGoal(goal.getGoalId());
		List<GoalTask> generated = goalService.generateTasks(goal.getGoalId());
		stubTaskRecords(generated);

		for (GoalTask link : generated) {
			orchestratorService.startTask(link.getTaskId());
		}

		GoalEvaluation evaluation = goalService.evaluateCompletion(goal.getGoalId());

		assertEquals(GoalStatus.FAILED, goal.getStatus());
		assertTrue(evaluation.progress() < 100.0);
		assertEvent(EventType.GOAL_FAILED);
		assertEvent(EventType.SESSION_FAILED);
	}

	@Test
	void generatedTasksAreLinkedToGoalAndMilestones() {
		wire(success(AgentType.HERMES), success(AgentType.CODEX),
			success(AgentType.TEST_AGENT));
		Goal goal = goalService.createGoal("project-x", "开发用户管理模块", "开发用户管理模块",
			GoalPriority.CRITICAL);
		goalService.decomposeGoal(goal.getGoalId());
		List<GoalTask> generated = goalService.generateTasks(goal.getGoalId());

		assertEquals(4, generated.size());
		assertEquals(3, goalService.getMilestones(goal.getGoalId()).size());
		for (GoalTask link : generated) {
			assertEquals(goal.getGoalId(), link.getGoalId());
			assertTrue(goalService.milestoneIdForTask(link.getTaskId()).isPresent());
		}
		EventRecord created = lastEvent(EventType.GOAL_TASK_CREATED);
		assertEquals(goal.getGoalId(), created.aggregateId());
		assertEquals("CRITICAL", created.metadata().get("priority"));
	}

	/** Builds the executor, runtime, orchestrator and goal service around the
	 * executors. */
	private void wire(AgentExecutor... executors) {
		ExecutionGraphExecutor executor = new ExecutionGraphExecutor(List.of(executors),
			auditService, taskCenterService, null, null, runtimeProvider,
			collaborationService);
		runtime = new AgentRuntimeService(sessionRepository, auditService, taskCenterService,
			traceService, graphBuilder, executor);
		runtimeProvider.value = runtime;
		orchestratorService = new OrchestratorService(queueRepository, auditService,
			taskCenterService, new AgentAutoSelectionService(
				new AgentSelector(mock(AgentManager.class)), agentOptimizationService,
				auditService), optimizationService, runtime, graphBuilder, memoryService,
			planningService);
		goalService = new GoalManagementService(goalRepository, milestoneRepository,
			goalTaskRepository, planningService, orchestratorService, taskCenterService,
			memoryService, optimizationService, runtime, auditService);
	}

	private void stubTaskRecords(List<GoalTask> generated) {
		for (GoalTask link : generated) {
			when(taskCenterService.getTask(link.getTaskId())).thenReturn(Optional.of(
				new TaskRecord(link.getTaskId(), "goal task", "Autonomous goal task",
					"project-x")));
		}
	}

	private AgentMetrics metrics(String agent, int total, int success, int failed,
			long averageDuration) {
		return new AgentMetrics("agent-" + agent, agent, total, success, failed, 0,
			averageDuration, java.time.Instant.now().minusSeconds(60), 0, 0);
	}

	private AgentExecutor success(AgentType type) {
		return new RecordingExecutor(type, context -> AgentExecutionResult.of(context,
			ExecutionNodeStatus.COMPLETED, "ok", null));
	}

	private AgentExecutor failure(AgentType type, String error) {
		return new RecordingExecutor(type, context -> AgentExecutionResult.of(context,
			ExecutionNodeStatus.FAILED, null, error));
	}

	private List<EventRecord> events() {
		return auditRepository.query(new EventQuery(null, null, null, null, null, null, null,
			null, null, null, java.util.Set.of(), null, null, 0, EventQuery.MAX_LIMIT));
	}

	private EventRecord lastEvent(EventType type) {
		return events().stream()
			.filter(event -> event.type() == type)
			.reduce((first, second) -> second)
			.orElseThrow(() -> new AssertionError("missing audit event " + type));
	}

	private void assertEvent(EventType type) {
		assertTrue(events().stream().anyMatch(event -> event.type() == type),
			"missing audit event " + type);
	}

	/** ObjectProvider whose value is assigned after both beans are built. */
	static final class MutableRuntimeProvider implements ObjectProvider<AgentRuntimeService> {

		private AgentRuntimeService value;

		@Override
		public AgentRuntimeService getObject() {
			return value;
		}

		@Override
		public AgentRuntimeService getObject(Object... args) {
			return value;
		}

		@Override
		public AgentRuntimeService getIfAvailable() {
			return value;
		}

		@Override
		public AgentRuntimeService getIfUnique() {
			return value;
		}

		@Override
		public AgentRuntimeService getIfAvailable(Supplier<AgentRuntimeService> defaultSupplier) {
			return value == null ? defaultSupplier.get() : value;
		}
	}

	/** Executes with a scripted result per agent type. */
	static class RecordingExecutor implements AgentExecutor {

		private final AgentType type;
		private final Function<AgentExecutionContext, AgentExecutionResult> behavior;

		RecordingExecutor(AgentType type,
				Function<AgentExecutionContext, AgentExecutionResult> behavior) {
			this.type = type;
			this.behavior = behavior;
		}

		@Override
		public AgentType type() {
			return type;
		}

		@Override
		public AgentExecutionResult execute(AgentExecutionContext context) {
			return behavior.apply(context);
		}
	}
}
