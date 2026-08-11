package com.aidevos.orchestrator.optimization;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

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
import com.aidevos.orchestrator.memory.InMemoryMemoryRepository;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.metrics.agent.AgentMetricsService;
import com.aidevos.orchestrator.observability.ExecutionTraceService;
import com.aidevos.orchestrator.observability.InMemoryTraceRepository;
import com.aidevos.orchestrator.observability.usage.UsageService;
import com.aidevos.orchestrator.orchestration.AgentExecutionContext;
import com.aidevos.orchestrator.orchestration.AgentExecutionResult;
import com.aidevos.orchestrator.orchestration.AgentExecutor;
import com.aidevos.orchestrator.orchestration.ExecutionGraphBuilder;
import com.aidevos.orchestrator.orchestration.ExecutionGraphExecutor;
import com.aidevos.orchestrator.orchestration.ExecutionNodeStatus;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.runtime.InMemoryAgentSessionRepository;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared wiring for the optimization tests: in-memory optimization /
 * session / team / message / approval / memory / trace / audit repositories,
 * a recording agent executor and a mutable ObjectProvider so the executor
 * and the runtime service can reference each other without a construction
 * cycle. The agent metrics service is mocked; scoring details are covered by
 * AgentScoreTest with the same wiring.
 */
abstract class OptimizationTestBase {

	protected final InMemoryOptimizationRepository optimizationRepository =
		new InMemoryOptimizationRepository();
	protected final InMemoryAgentSessionRepository sessionRepository =
		new InMemoryAgentSessionRepository();
	protected final InMemoryAgentTeamRepository teamRepository =
		new InMemoryAgentTeamRepository();
	protected final InMemoryAgentMessageRepository messageRepository =
		new InMemoryAgentMessageRepository();
	protected final InMemoryHumanApprovalRepository approvalRepository =
		new InMemoryHumanApprovalRepository();
	protected final InMemoryHumanFeedbackRepository feedbackRepository =
		new InMemoryHumanFeedbackRepository();
	protected final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
	protected final AuditService auditService = new AuditService(auditRepository);
	protected final InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
	protected final MemoryService memoryService = new MemoryService(memoryRepository);
	protected final ExecutionTraceService traceService =
		new ExecutionTraceService(new InMemoryTraceRepository());
	protected final ExecutionGraphBuilder graphBuilder = new ExecutionGraphBuilder();
	protected final TaskCenterService taskCenterService = mock(TaskCenterService.class);
	protected final AgentMetricsService metricsService = mock(AgentMetricsService.class);
	protected final UsageService usageService = mock(UsageService.class);
	protected final MutableRuntimeProvider runtimeProvider = new MutableRuntimeProvider();
	protected final AgentCollaborationService collaborationService =
		new AgentCollaborationService(teamRepository, messageRepository, auditService,
			memoryService, taskCenterService);
	protected final AgentOptimizationService agentOptimizationService =
		new AgentOptimizationService(metricsService, traceService, usageService,
			teamRepository, messageRepository, approvalRepository, auditService);
	protected ExecutionGraphExecutor graphExecutor;
	protected OptimizationService optimizationService;

	OptimizationTestBase() {
		when(usageService.getAgentUsage(org.mockito.ArgumentMatchers.anyString()))
			.thenReturn(com.aidevos.orchestrator.observability.usage.UsageSummary.empty());
		when(metricsService.listAgentMetrics()).thenReturn(List.of(
			metrics("HERMES", 10, 8, 2, 900),
			metrics("CODEX", 10, 9, 1, 1200),
			metrics("TEST_AGENT", 10, 7, 3, 1500)));
	}

	private com.aidevos.orchestrator.metrics.agent.AgentMetrics metrics(String agent,
			int total, int success, int failed, long averageDuration) {
		return new com.aidevos.orchestrator.metrics.agent.AgentMetrics(
			"agent-" + agent, agent, total, success, failed, 0, averageDuration,
			java.time.Instant.now().minusSeconds(60), 0, 0);
	}

	protected AgentRuntimeService runtime(AgentExecutor... executors) {
		graphExecutor = new ExecutionGraphExecutor(List.of(executors), auditService,
			taskCenterService, null, null, runtimeProvider, collaborationService);
		AgentRuntimeService runtime = new AgentRuntimeService(sessionRepository, auditService,
			taskCenterService, traceService, graphBuilder, graphExecutor);
		runtimeProvider.value = runtime;
		optimizationService = new OptimizationService(optimizationRepository, auditService,
			memoryService, taskCenterService, agentOptimizationService, traceService,
			runtime, collaborationService);
		return runtime;
	}

	protected TaskRecord task(String taskId) {
		TaskRecord task = new TaskRecord(taskId, "Implement login", "Append a line to a.txt",
			"project-x", "workspace-1");
		when(taskCenterService.getTask(taskId)).thenReturn(Optional.of(task));
		return task;
	}

	protected RecordingExecutor success(AgentType type) {
		return new RecordingExecutor(type, context -> AgentExecutionResult.of(context,
			ExecutionNodeStatus.COMPLETED, "ok", null));
	}

	protected RecordingExecutor failure(AgentType type, String error) {
		return new RecordingExecutor(type, context -> AgentExecutionResult.of(context,
			ExecutionNodeStatus.FAILED, null, error));
	}

	protected List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
	}

	protected EventRecord lastEvent(EventType type) {
		return events().stream()
			.filter(event -> event.type() == type)
			.reduce((first, second) -> second)
			.orElseThrow(() -> new AssertionError("missing audit event " + type));
	}

	protected void assertEvent(EventType type) {
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
		public AgentRuntimeService getIfAvailable(
				java.util.function.Supplier<AgentRuntimeService> defaultSupplier) {
			return value == null ? defaultSupplier.get() : value;
		}
	}

	/** Records how often an agent type was executed and returns a scripted result. */
	static class RecordingExecutor implements AgentExecutor {

		private final AgentType type;
		private final Function<AgentExecutionContext, AgentExecutionResult> behavior;
		int calls;

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
			calls++;
			return behavior.apply(context);
		}
	}
}
