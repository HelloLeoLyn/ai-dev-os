package com.aidevos.orchestrator.runtime;

import java.util.List;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.metrics.agent.AgentMetricsService;
import com.aidevos.orchestrator.metrics.tool.ToolMetricsService;
import com.aidevos.orchestrator.observability.ObservabilityService;
import com.aidevos.orchestrator.observability.TaskObservability;
import com.aidevos.orchestrator.observability.TraceStatus;
import com.aidevos.orchestrator.observability.usage.UsageService;
import com.aidevos.orchestrator.orchestration.AgentExecutionContext;
import com.aidevos.orchestrator.orchestration.AgentExecutionResult;
import com.aidevos.orchestrator.orchestration.ExecutionNodeStatus;
import com.aidevos.orchestrator.timeline.TimelineService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Integration verification of the runtime recovery: a failed node is
 * checkpointed and the session is retried from that node on resume; a paused
 * session continues after its current node; the session appears in the task
 * observability bundle and in the execution traces.
 */
class RuntimeGraphRecoveryIntegrationTest extends RuntimeTestBase {

	@Test
	void resumeAfterFailureRecoversFromFailedNodeAndCompletes() {
		task("task-1");
		RecordingExecutor hermes = success(AgentType.HERMES);
		int[] codexCalls = new int[1];
		RecordingExecutor codex = new RecordingExecutor(AgentType.CODEX, context -> {
			codexCalls[0]++;
			if (codexCalls[0] == 1) {
				return AgentExecutionResult.of(context, ExecutionNodeStatus.FAILED, null,
					"code broke");
			}
			return AgentExecutionResult.of(context, ExecutionNodeStatus.COMPLETED,
				"code ok", null);
		}, null);
		RecordingExecutor test = success(AgentType.TEST_AGENT);
		AgentRuntimeService runtime = runtime(hermes, codex, test);

		AgentSession failed = runtime.startSession("task-1");
		assertEquals(AgentSessionStatus.FAILED, failed.getStatus());
		assertEquals("CODEX_IMPLEMENTATION", failed.getCurrentNodeId());
		assertEquals(1, codexCalls[0]);
		assertEquals(0, test.calls);
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.SESSION_FAILED));

		AgentSession completed = runtime.resumeSession(failed.getSessionId());
		assertEquals(AgentSessionStatus.COMPLETED, completed.getStatus());
		assertEquals("TEST_AGENT_VERIFY", completed.getCurrentNodeId());
		assertEquals(1, hermes.calls, "completed nodes must not re-run");
		assertEquals(2, codexCalls[0], "failed node must be retried on resume");
		assertEquals(1, test.calls);
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.SESSION_RESUMED));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.SESSION_COMPLETED));
	}

	@Test
	void resumedPausedSessionContinuesAfterCurrentNode() {
		task("task-1");
		RecordingExecutor hermes = success(AgentType.HERMES);
		RecordingExecutor codex = success(AgentType.CODEX);
		RecordingExecutor test = success(AgentType.TEST_AGENT);
		AgentRuntimeService runtime = runtime(hermes, codex, test);

		AgentSession session = seed("session-1", "task-1", AgentSessionStatus.RUNNING);
		session.setCurrentNodeId("HERMES_PLANNING");
		session.markPaused();
		repository.save(session);

		AgentSession resumed = runtime.resumeSession("session-1");

		assertEquals(AgentSessionStatus.COMPLETED, resumed.getStatus());
		assertEquals(0, hermes.calls, "nodes before the current node must not re-run");
		assertEquals(1, codex.calls);
		assertEquals(1, test.calls);
		assertEquals("TEST_AGENT_VERIFY", resumed.getCurrentNodeId());
	}

	@Test
	void sessionAppearsInTaskObservabilityAndTraces() {
		task("task-1");
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));

		AgentSession session = runtime.startSession("task-1");

		assertTrue(traceService.listByTask("task-1").stream()
			.anyMatch(trace -> trace.getGraphId() != null
				&& trace.getStatus() == TraceStatus.SUCCESS),
			"session must produce a completed trace for the task");

		ObservabilityService observability = new ObservabilityService(taskCenterService,
			mock(ExecutionRecordManager.class), mock(AgentMetricsService.class),
			traceService, mock(UsageService.class), mock(ToolMetricsService.class),
			mock(TimelineService.class), runtime);
		TaskObservability bundle = observability.taskObservability("task-1");

		assertEquals(List.of(session.getSessionId()),
			bundle.sessions().stream().map(AgentSession::getSessionId).toList());
		assertTrue(bundle.traces().stream().anyMatch(trace ->
			trace.getGraphId() != null && trace.getStatus() == TraceStatus.SUCCESS));
	}
}
