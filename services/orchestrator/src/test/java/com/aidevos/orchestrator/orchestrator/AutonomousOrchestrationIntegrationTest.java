package com.aidevos.orchestrator.orchestrator;

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
 * Autonomous orchestration integration verification: a task is submitted,
 * scheduled, auto-assigned and started through the real runtime; the dynamic
 * graph runs, the session completes and the orchestration task finishes
 * COMPLETED with the full audit trail (ORCHESTRATOR_STARTED / TASK_QUEUED /
 * AGENT_AUTO_SELECTED / DYNAMIC_GRAPH_CREATED / SESSION_STARTED /
 * SESSION_COMPLETED).
 */
class AutonomousOrchestrationIntegrationTest {

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
		agentOptimizationService, traceService, null, collaborationService);
	private OrchestratorService orchestratorService;
	private AgentRuntimeService runtime;

	AutonomousOrchestrationIntegrationTest() {
		// Collaborators are wired in the constructor; the executor, runtime and
		// orchestrator are built per test through orchestrator(executors...)
		// so each test scripts its own agent behavior.
	}

	/** Builds the executor, runtime and orchestrator around the executors. */
	private OrchestratorService orchestrator(AgentExecutor... executors) {
		ExecutionGraphExecutor executor = new ExecutionGraphExecutor(List.of(executors),
			auditService, taskCenterService, null, null, runtimeProvider,
			collaborationService);
		runtime = new AgentRuntimeService(sessionRepository, auditService, taskCenterService,
			traceService, graphBuilder, executor);
		runtimeProvider.value = runtime;
		orchestratorService = new OrchestratorService(queueRepository, auditService,
			taskCenterService, new AgentAutoSelectionService(
				new AgentSelector(mock(AgentManager.class)), agentOptimizationService,
				auditService), optimizationService, runtime, graphBuilder, memoryService);
		return orchestratorService;
	}

	@BeforeEach
	void setUp() {
		when(taskCenterService.getTask("task-1"))
			.thenReturn(Optional.of(task("task-1")));
		when(usageService.getAgentUsage(anyString())).thenReturn(UsageSummary.empty());
		when(metricsService.listAgentMetrics()).thenReturn(List.of(
			metrics("HERMES", 10, 8, 2, 900),
			metrics("CODEX", 10, 9, 1, 1200),
			metrics("TEST_AGENT", 10, 7, 3, 1500)));
	}

	@Test
	void fullOrchestrationFlowCompletesTask() {
		orchestrator(success(AgentType.HERMES), success(AgentType.CODEX),
			success(AgentType.TEST_AGENT));
		orchestratorService.submitTask("task-1", "CODE_GENERATION",
			TaskPriority.CRITICAL, List.of("codex"));

		OrchestrationTask scheduled = orchestratorService.scheduleNextTask().orElseThrow();
		assertEquals("task-1", scheduled.getTaskId());

		List<String> assigned = orchestratorService.assignAgents("task-1");
		assertEquals(List.of("HERMES", "CODEX", "TEST_AGENT"), assigned);

		OrchestrationTask finished = orchestratorService.startTask("task-1");

		assertEquals(OrchestrationTaskStatus.COMPLETED, finished.getStatus());
		assertEquals(TaskPoolStatus.COMPLETED, orchestratorService.getPool().getStatus());
		AgentSession session = runtime.sessionsForTask("task-1").get(0);
		assertEquals(AgentSessionStatus.COMPLETED, session.getStatus());
		assertEvent(EventType.ORCHESTRATOR_STARTED);
		assertEvent(EventType.TASK_QUEUED);
		assertEvent(EventType.AGENT_AUTO_SELECTED);
		assertEvent(EventType.DYNAMIC_GRAPH_CREATED);
		assertEvent(EventType.SESSION_STARTED);
		assertEvent(EventType.SESSION_COMPLETED);
		var queued = lastEvent(EventType.TASK_QUEUED);
		assertEquals("task-1", queued.taskId());
		assertEquals("CRITICAL", queued.metadata().get("priority"));
		var graphEvent = lastEvent(EventType.DYNAMIC_GRAPH_CREATED);
		assertEquals("CODE_GENERATION", graphEvent.metadata().get("taskType"));
	}

	@Test
	void failingNodeFailsOrchestrationTask() {
		orchestrator(success(AgentType.HERMES), failure(AgentType.CODEX, "compile error"),
			success(AgentType.TEST_AGENT));
		orchestratorService.submitTask("task-1", "CODE_GENERATION", TaskPriority.NORMAL, null);

		OrchestrationTask finished = orchestratorService.startTask("task-1");

		assertEquals(OrchestrationTaskStatus.FAILED, finished.getStatus());
		assertTrue(finished.getErrorMessage().contains("Session failed"));
		assertEvent(EventType.SESSION_FAILED);
		assertEvent(EventType.DYNAMIC_GRAPH_CREATED);
	}

	@Test
	void prioritizedQueueSchedulesHighestFirst() {
		orchestrator();
		orchestratorService.submitTask("task-1", "CODE_GENERATION", TaskPriority.LOW, null);
		orchestratorService.submitTask("task-2", "CODE_GENERATION", TaskPriority.HIGH, null);

		OrchestrationTask next = orchestratorService.scheduleNextTask().orElseThrow();

		assertEquals("task-2", next.getTaskId());
		assertEquals(TaskPriority.HIGH, next.getPriority());
		assertEquals(OrchestrationTaskStatus.QUEUED,
			orchestratorService.getPool().get("task-1").getStatus());
	}

	private TaskRecord task(String taskId) {
		return new TaskRecord(taskId, "Implement login", "Append a line to a.txt",
			"project-x", "workspace-1");
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
		return auditRepository.query(EventQuery.all());
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
