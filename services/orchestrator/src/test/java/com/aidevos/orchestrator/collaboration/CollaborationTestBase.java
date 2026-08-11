package com.aidevos.orchestrator.collaboration;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.memory.InMemoryMemoryRepository;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.observability.ExecutionTraceService;
import com.aidevos.orchestrator.observability.InMemoryTraceRepository;
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
 * Shared wiring for the collaboration tests: in-memory team / message /
 * memory / audit repositories, a recording agent executor and a mutable
 * ObjectProvider so the executor and the runtime service can reference each
 * other without a construction cycle.
 */
abstract class CollaborationTestBase {

	protected final InMemoryAgentSessionRepository sessionRepository =
		new InMemoryAgentSessionRepository();
	protected final InMemoryAgentTeamRepository teamRepository =
		new InMemoryAgentTeamRepository();
	protected final InMemoryAgentMessageRepository messageRepository =
		new InMemoryAgentMessageRepository();
	protected final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
	protected final AuditService auditService = new AuditService(auditRepository);
	protected final InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
	protected final MemoryService memoryService = new MemoryService(memoryRepository);
	protected final ExecutionTraceService traceService =
		new ExecutionTraceService(new InMemoryTraceRepository());
	protected final ExecutionGraphBuilder graphBuilder = new ExecutionGraphBuilder();
	protected final TaskCenterService taskCenterService = mock(TaskCenterService.class);
	protected final MutableRuntimeProvider runtimeProvider = new MutableRuntimeProvider();
	protected final AgentCollaborationService collaborationService =
		new AgentCollaborationService(teamRepository, messageRepository, auditService,
			memoryService, taskCenterService);
	protected ExecutionGraphExecutor graphExecutor;

	protected AgentRuntimeService runtime(AgentExecutor... executors) {
		graphExecutor = new ExecutionGraphExecutor(List.of(executors), auditService,
			taskCenterService, null, null, runtimeProvider, collaborationService);
		AgentRuntimeService runtime = new AgentRuntimeService(sessionRepository, auditService,
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
			if (callOrder != null) {
				callOrder.add(type.name());
			}
			return behavior.apply(context);
		}
	}
}
