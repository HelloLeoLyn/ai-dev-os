package com.aidevos.orchestrator.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ExecutionGraphExecutorTest {

	private final ExecutionGraphBuilder builder = new ExecutionGraphBuilder();
	private final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();

	private ExecutionGraphExecutor executor(AgentExecutor... executors) {
		return new ExecutionGraphExecutor(List.of(executors), new AuditService(auditRepository),
			mock(TaskCenterService.class));
	}

	private AgentExecutionContext context(String taskId, String graphId) {
		TaskRecord task = new TaskRecord(taskId, "Implement login",
			"Append a line to a.txt", "project-x", "workspace-1");
		AgentExecutionContext context = new AgentExecutionContext();
		context.setTaskId(taskId);
		context.setTask(task);
		context.setWorkspaceId("workspace-1");
		context.setWorkspacePath("/tmp/repo");
		context.setGraphId(graphId);
		context.setInput("Append a line to a.txt");
		return context;
	}

	@Test
	void shouldExecuteNodesInTopologyOrder() {
		List<String> callOrder = new ArrayList<>();
		RecordingExecutor hermes = recording(AgentType.HERMES, success("plan ok"), callOrder);
		RecordingExecutor codex = recording(AgentType.CODEX, success("code ok"), callOrder);
		RecordingExecutor test = recording(AgentType.TEST_AGENT, success("test ok"), callOrder);
		ExecutionGraphExecutor executor = executor(hermes, codex, test);

		ExecutionGraph graph = builder.build("task-1", "CODE_TASK");
		executor.execute(graph, context("task-1", graph.getGraphId()));

		assertEquals(List.of("HERMES", "CODEX", "TEST_AGENT"), callOrder);
		assertEquals(ExecutionNodeStatus.COMPLETED, graph.getNode("HERMES_PLANNING").getStatus());
		assertEquals(ExecutionNodeStatus.COMPLETED, graph.getNode("CODEX_IMPLEMENTATION").getStatus());
		assertEquals(ExecutionNodeStatus.COMPLETED, graph.getNode("TEST_AGENT_VERIFY").getStatus());
		assertEvent(EventType.NODE_STARTED);
		assertEvent(EventType.NODE_COMPLETED);
	}

	@Test
	void shouldStopDownstreamNodesOnFailure() {
		RecordingExecutor hermes = recording(AgentType.HERMES, success("plan ok"));
		RecordingExecutor codex = recording(AgentType.CODEX, failure("code broke"));
		RecordingExecutor test = recording(AgentType.TEST_AGENT, success("test ok"));
		ExecutionGraphExecutor executor = executor(hermes, codex, test);

		ExecutionGraph graph = builder.build("task-1", "CODE_TASK");
		executor.execute(graph, context("task-1", graph.getGraphId()));

		assertEquals(ExecutionNodeStatus.COMPLETED, graph.getNode("HERMES_PLANNING").getStatus());
		assertEquals(ExecutionNodeStatus.FAILED, graph.getNode("CODEX_IMPLEMENTATION").getStatus());
		assertEquals(ExecutionNodeStatus.PENDING, graph.getNode("TEST_AGENT_VERIFY").getStatus());
		assertEquals(0, test.calls);
		assertEvent(EventType.NODE_FAILED);
		EventRecord failed = events().stream()
			.filter(event -> event.type() == EventType.NODE_FAILED).findFirst().orElseThrow();
		assertEquals("CODEX_IMPLEMENTATION", failed.metadata().get("nodeId"));
		assertEquals("CODEX", failed.metadata().get("agentType"));
		assertNotNull(failed.metadata().get("duration"));
	}

	@Test
	void shouldRetryRepairLoopUntilVerifyPasses() {
		RecordingExecutor repair = recording(AgentType.REPAIR_AGENT, success("repair plan"));
		RecordingExecutor fix = recording(AgentType.CODEX, success("fix applied"));
		int[] verifyCalls = new int[1];
		RecordingExecutor test = new RecordingExecutor(AgentType.TEST_AGENT, context -> {
			if ("TEST_AGENT_ANALYZE".equals(context.getNodeId())) {
				return AgentExecutionResult.of(context, ExecutionNodeStatus.COMPLETED,
					"Test failed: flaky", null);
			}
			verifyCalls[0]++;
			if (verifyCalls[0] < 3) {
				return AgentExecutionResult.of(context, ExecutionNodeStatus.FAILED, null,
					"Tests failed: still broken");
			}
			return AgentExecutionResult.of(context, ExecutionNodeStatus.COMPLETED,
				"Tests passed", null);
		}, null);
		ExecutionGraphExecutor executor = executor(test, repair, fix);

		ExecutionGraph graph = builder.build("task-1", "REPAIR_TASK");
		executor.execute(graph, context("task-1", graph.getGraphId()));

		assertEquals(1, test.calls - verifyCalls[0], "analyze node runs once");
		assertEquals(3, repair.calls, "repair re-enters up to maxAttempts");
		assertEquals(3, fix.calls);
		assertEquals(3, verifyCalls[0]);
		assertEquals(ExecutionNodeStatus.COMPLETED, graph.getNode("TEST_AGENT_VERIFY").getStatus());
		assertEquals(ExecutionNodeStatus.COMPLETED, graph.getNode("REPAIR_AGENT_ANALYZE").getStatus());
	}

	@Test
	void shouldStopRepairLoopAfterMaxAttempts() {
		RecordingExecutor repair = recording(AgentType.REPAIR_AGENT, success("repair plan"));
		RecordingExecutor fix = recording(AgentType.CODEX, success("fix applied"));
		int[] verifyCalls = new int[1];
		RecordingExecutor test = new RecordingExecutor(AgentType.TEST_AGENT, context -> {
			if ("TEST_AGENT_ANALYZE".equals(context.getNodeId())) {
				return AgentExecutionResult.of(context, ExecutionNodeStatus.COMPLETED,
					"Test failed: broken", null);
			}
			verifyCalls[0]++;
			return AgentExecutionResult.of(context, ExecutionNodeStatus.FAILED, null,
				"Tests failed: broken");
		}, null);
		ExecutionGraphExecutor executor = executor(test, repair, fix);

		ExecutionGraph graph = builder.build("task-1", "REPAIR_TASK");
		executor.execute(graph, context("task-1", graph.getGraphId()));

		assertEquals(3, repair.calls);
		assertEquals(3, fix.calls);
		assertEquals(3, verifyCalls[0]);
		assertEquals(ExecutionNodeStatus.FAILED, graph.getNode("TEST_AGENT_VERIFY").getStatus());
	}

	private RecordingExecutor recording(AgentType type, Function<AgentExecutionContext,
			AgentExecutionResult> behavior) {
		return new RecordingExecutor(type, behavior, null);
	}

	private RecordingExecutor recording(AgentType type, Function<AgentExecutionContext,
			AgentExecutionResult> behavior, List<String> callOrder) {
		return new RecordingExecutor(type, behavior, callOrder);
	}

	private Function<AgentExecutionContext, AgentExecutionResult> success(String output) {
		return context -> AgentExecutionResult.of(context, ExecutionNodeStatus.COMPLETED,
			output, null);
	}

	private Function<AgentExecutionContext, AgentExecutionResult> failure(String error) {
		return context -> AgentExecutionResult.of(context, ExecutionNodeStatus.FAILED,
			null, error);
	}

	private void assertEvent(EventType type) {
		assertTrue(events().stream().anyMatch(event -> event.type() == type),
			"missing audit event " + type);
	}

	private List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
	}

	private static final class RecordingExecutor implements AgentExecutor {

		private final AgentType type;
		private final Function<AgentExecutionContext, AgentExecutionResult> behavior;
		private final List<String> callOrder;
		private int calls;
		private AgentExecutionContext lastContext;

		private RecordingExecutor(AgentType type,
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
