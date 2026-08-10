package com.aidevos.orchestrator.runtime;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.observability.ExecutionTraceService;
import com.aidevos.orchestrator.observability.InMemoryTraceRepository;
import com.aidevos.orchestrator.orchestration.AgentExecutionContext;
import com.aidevos.orchestrator.orchestration.AgentExecutionResult;
import com.aidevos.orchestrator.orchestration.AgentExecutor;
import com.aidevos.orchestrator.orchestration.ExecutionGraphBuilder;
import com.aidevos.orchestrator.orchestration.ExecutionGraphExecutor;
import com.aidevos.orchestrator.orchestration.ExecutionNodeStatus;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared wiring for the runtime tests: in-memory repositories, recording
 * agent executors and a mutable ObjectProvider so the executor and the
 * runtime service can reference each other without a construction cycle.
 */
abstract class RuntimeTestBase {

	protected final InMemoryAgentSessionRepository repository = new InMemoryAgentSessionRepository();
	protected final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
	protected final AuditService auditService = new AuditService(auditRepository);
	protected final ExecutionTraceService traceService =
		new ExecutionTraceService(new InMemoryTraceRepository());
	protected final ExecutionGraphBuilder graphBuilder = new ExecutionGraphBuilder();
	protected final TaskCenterService taskCenterService = mock(TaskCenterService.class);
	protected final MutableRuntimeProvider runtimeProvider = new MutableRuntimeProvider();

	protected AgentRuntimeService runtime(AgentExecutor... executors) {
		ExecutionGraphExecutor graphExecutor = new ExecutionGraphExecutor(List.of(executors),
			auditService, taskCenterService, null, null, runtimeProvider);
		AgentRuntimeService runtime = new AgentRuntimeService(repository, auditService,
			taskCenterService, traceService, graphBuilder, graphExecutor);
		runtimeProvider.value = runtime;
		return runtime;
	}

	protected TaskRecord task(String taskId) {
		TaskRecord task = new TaskRecord(taskId, "Implement login", "Append a line to a.txt",
			"project-x", "workspace-1");
		when(taskCenterService.getTask(taskId)).thenReturn(Optional.of(task));
		return task;
	}

	protected AgentSession seed(String sessionId, String taskId, AgentSessionStatus status) {
		AgentSession session = new AgentSession(sessionId, taskId, "graph-" + sessionId);
		if (status != AgentSessionStatus.CREATED) {
			session.markRunning();
			if (status == AgentSessionStatus.PAUSED) {
				session.markPaused();
			}
			else if (status == AgentSessionStatus.FAILED) {
				session.markFailed();
			}
			else if (status == AgentSessionStatus.STOPPED) {
				session.markStopped();
			}
			else if (status == AgentSessionStatus.COMPLETED) {
				session.markCompleted();
			}
		}
		repository.save(session);
		return session;
	}

	protected RecordingExecutor success(AgentType type) {
		return new RecordingExecutor(type, context -> AgentExecutionResult.of(context,
			ExecutionNodeStatus.COMPLETED, "ok", null), null);
	}

	protected RecordingExecutor failure(AgentType type, String error) {
		return new RecordingExecutor(type, context -> AgentExecutionResult.of(context,
			ExecutionNodeStatus.FAILED, null, error), null);
	}

	protected List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
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

	/** Records how often an agent type was executed and returns a scripted result. */
	static class RecordingExecutor implements AgentExecutor {

		private final AgentType type;
		private final Function<AgentExecutionContext, AgentExecutionResult> behavior;
		private final List<String> callOrder;
		int calls;
		private AgentExecutionContext lastContext;

		RecordingExecutor(AgentType type,
				Function<AgentExecutionContext, AgentExecutionResult> behavior,
				List<String> callOrder) {
			this.type = type;
			this.behavior = behavior;
			this.callOrder = callOrder;
		}

		@Override
		public AgentType type() {
			return type;
		}

		@Override
		public AgentExecutionResult execute(AgentExecutionContext context) {
			calls++;
			lastContext = context;
			if (callOrder != null) {
				callOrder.add(type.name());
			}
			return behavior.apply(context);
		}
	}
}
