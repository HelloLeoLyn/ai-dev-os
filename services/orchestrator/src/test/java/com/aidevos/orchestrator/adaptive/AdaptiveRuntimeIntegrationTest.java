package com.aidevos.orchestrator.adaptive;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
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
import com.aidevos.orchestrator.orchestration.ExecutionGraph;
import com.aidevos.orchestrator.orchestration.ExecutionGraphBuilder;
import com.aidevos.orchestrator.orchestration.ExecutionGraphExecutor;
import com.aidevos.orchestrator.orchestration.ExecutionNodeStatus;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Adaptive runtime integration verification: a running graph node that fails
 * is retried (RETRY), switched to a better-scored agent (SWITCH_AGENT) or
 * the task is replanned (REPLAN) before the session is failed. The session
 * still completes when an adaptation succeeds and the feedback -> decision ->
 * replan audit trail is emitted on the task timeline.
 */
class AdaptiveRuntimeIntegrationTest {

	private final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
	private final AuditService auditService = new AuditService(auditRepository);
	private final InMemoryAgentSessionRepository sessionRepository =
		new InMemoryAgentSessionRepository();
	private final InMemoryOptimizationRepository optimizationRepository =
		new InMemoryOptimizationRepository();
	private final InMemoryAgentTeamRepository teamRepository =
		new InMemoryAgentTeamRepository();
	private final InMemoryAgentMessageRepository messageRepository =
		new InMemoryAgentMessageRepository();
	private final InMemoryHumanApprovalRepository approvalRepository =
		new InMemoryHumanApprovalRepository();
	private final InMemoryHumanFeedbackRepository feedbackRepository =
		new InMemoryHumanFeedbackRepository();
	private final InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
	private final MemoryService memoryService = new MemoryService(memoryRepository);
	private final ExecutionTraceService traceService =
		new ExecutionTraceService(new InMemoryTraceRepository());
	private final ExecutionGraphBuilder graphBuilder = new ExecutionGraphBuilder();
	private final TaskCenterService taskCenterService = mock(TaskCenterService.class);
	private final AgentMetricsService metricsService = mock(AgentMetricsService.class);
	private final UsageService usageService = mock(UsageService.class);
	private final MutableRuntimeProvider runtimeProvider = new MutableRuntimeProvider();
	private final MutableAdaptiveProvider adaptiveProvider = new MutableAdaptiveProvider();
	private final AgentOptimizationService agentOptimizationService =
		new AgentOptimizationService(metricsService, traceService, usageService,
			teamRepository, messageRepository, approvalRepository, auditService);
	private final OptimizationService optimizationService = new OptimizationService(
		optimizationRepository, auditService, memoryService, taskCenterService,
		agentOptimizationService);
	private final PlanningService planningService = new PlanningService(taskCenterService,
		memoryService, optimizationService, agentOptimizationService,
		new com.aidevos.orchestrator.agent.AgentSelector(mock(AgentManager.class)),
		graphBuilder, auditService);
	private AdaptiveExecutionService adaptiveService;
	private AgentRuntimeService runtime;

	@BeforeEach
	void setUp() {
		when(taskCenterService.getTask("task-1"))
			.thenReturn(Optional.of(task("task-1")));
		when(usageService.getAgentUsage(anyString())).thenReturn(UsageSummary.empty());
		when(metricsService.listAgentMetrics()).thenReturn(List.of(
			metrics("HERMES", 10, 8, 2, 900),
			metrics("CODEX", 10, 3, 7, 1500),
			metrics("OPENCLAW", 10, 9, 1, 800),
			metrics("TEST_AGENT", 10, 7, 3, 1500)));
		adaptiveService = new AdaptiveExecutionService(auditService, taskCenterService,
			agentOptimizationService, optimizationService, memoryService, planningService,
			null);
		adaptiveProvider.value = adaptiveService;
	}

	@Test
	void failedNodeIsRetriedAndSessionCompletes() {
		executor(success(AgentType.HERMES), flaky(AgentType.CODEX, 1),
			success(AgentType.TEST_AGENT));

		AgentSession session = runtime.startSession("task-1",
			graphBuilder.build("task-1", "CODE_TASK"));

		assertEquals(AgentSessionStatus.COMPLETED, session.getStatus());
		assertEvent(EventType.EXECUTION_FEEDBACK_RECEIVED);
		assertEvent(EventType.ADAPTATION_STARTED);
		assertEvent(EventType.ADAPTATION_DECIDED);
		assertEvent(EventType.SESSION_COMPLETED);
		assertFalse(hasEvent(EventType.SESSION_FAILED));
		assertEquals(AdaptationAction.RETRY,
			adaptiveService.decisionsForTask("task-1").get(0).getAction());
		assertEquals(1, adaptiveService.feedbacksForTask("task-1").size());
	}

	@Test
	void failedNodeSwitchesAgentAndSessionCompletes() {
		executor(success(AgentType.HERMES), flaky(AgentType.CODEX, 2),
			success(AgentType.OPENCLAW), success(AgentType.TEST_AGENT));

		AgentSession session = runtime.startSession("task-1",
			graphBuilder.build("task-1", "CODE_TASK"));

		assertEquals(AgentSessionStatus.COMPLETED, session.getStatus());
		assertEvent(EventType.SESSION_COMPLETED);
		assertTrue(adaptiveService.decisionsForTask("task-1").stream()
			.anyMatch(decision -> decision.getAction() == AdaptationAction.SWITCH_AGENT
				&& "OPENCLAW".equals(decision.getTargetAgent())));
		assertEquals(2, adaptiveService.feedbacksForTask("task-1").size());
	}

	@Test
	void persistentFailureReplansThenFailsSession() {
		executor(success(AgentType.HERMES), alwaysFail(AgentType.CODEX),
			alwaysFail(AgentType.OPENCLAW), success(AgentType.TEST_AGENT));

		AgentSession session = runtime.startSession("task-1",
			graphBuilder.build("task-1", "CODE_TASK"));

		assertEquals(AgentSessionStatus.FAILED, session.getStatus());
		assertEvent(EventType.GRAPH_REPLANNED);
		assertEvent(EventType.SESSION_FAILED);
		assertTrue(adaptiveService.decisionsForTask("task-1").stream()
			.anyMatch(decision -> decision.getAction() == AdaptationAction.REPLAN));
		assertEquals(1, adaptiveService.replansForTask("task-1").size());
		assertTrue(adaptiveService.feedbacksForTask("task-1").size() >= 3);
	}

	/** Builds the executor and runtime around the executors. */
	private void executor(AgentExecutor... executors) {
		ExecutionGraphExecutor executor = new ExecutionGraphExecutor(List.of(executors),
			auditService, taskCenterService, null, traceService, runtimeProvider, null, null,
			adaptiveProvider);
		runtime = new AgentRuntimeService(sessionRepository, auditService, taskCenterService,
			traceService, graphBuilder, executor);
		runtimeProvider.value = runtime;
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
		return new FlakyExecutor(type, 0);
	}

	private AgentExecutor flaky(AgentType type, int failTimes) {
		return new FlakyExecutor(type, failTimes);
	}

	private AgentExecutor alwaysFail(AgentType type) {
		return new FlakyExecutor(type, Integer.MAX_VALUE);
	}

	private List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
	}

	private boolean hasEvent(EventType type) {
		return events().stream().anyMatch(event -> event.type() == type);
	}

	private void assertEvent(EventType type) {
		assertTrue(hasEvent(type), "missing audit event " + type);
	}

	/** ObjectProvider whose value is assigned after the bean is built. */
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

	/** ObjectProvider whose value is assigned after the bean is built. */
	static final class MutableAdaptiveProvider
			implements ObjectProvider<AdaptiveExecutionService> {

		private AdaptiveExecutionService value;

		@Override
		public AdaptiveExecutionService getObject() {
			return value;
		}

		@Override
		public AdaptiveExecutionService getObject(Object... args) {
			return value;
		}

		@Override
		public AdaptiveExecutionService getIfAvailable() {
			return value;
		}

		@Override
		public AdaptiveExecutionService getIfUnique() {
			return value;
		}

		@Override
		public AdaptiveExecutionService getIfAvailable(
				Supplier<AdaptiveExecutionService> defaultSupplier) {
			return value == null ? defaultSupplier.get() : value;
		}
	}

	/** Executes with a scripted result; fails the first N invocations. */
	static class FlakyExecutor implements AgentExecutor {

		private final AgentType type;
		private final int failTimes;
		private int calls;

		FlakyExecutor(AgentType type, int failTimes) {
			this.type = type;
			this.failTimes = failTimes;
		}

		@Override
		public AgentType type() {
			return type;
		}

		@Override
		public AgentExecutionResult execute(AgentExecutionContext context) {
			if (calls++ < failTimes) {
				return AgentExecutionResult.of(context, ExecutionNodeStatus.FAILED, null,
					"flaky failure of " + type);
			}
			return AgentExecutionResult.of(context, ExecutionNodeStatus.COMPLETED, "ok", null);
		}
	}
}
