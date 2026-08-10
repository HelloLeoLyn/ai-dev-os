package com.aidevos.orchestrator.runtime;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.orchestration.AgentExecutionContext;
import com.aidevos.orchestrator.orchestration.AgentExecutionResult;
import com.aidevos.orchestrator.orchestration.ExecutionNodeStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Session lifecycle verification: start -> running (observed during node
 * execution) -> completed, pause -> resume continuing the run, and stop
 * terminating the session.
 */
class AgentSessionLifecycleTest extends RuntimeTestBase {

	@Test
	void startRunsTheSessionAsRunningThenCompletes() {
		task("task-1");
		AgentSessionStatus[] observed = new AgentSessionStatus[1];
		AgentRuntimeService runtime = runtime(
			new RecordingExecutor(AgentType.HERMES, context -> {
				observed[0] = repository.get(repository.listByTask("task-1").getFirst()
					.getSessionId()).getStatus();
				return completed(context);
			}, null),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));

		AgentSession session = runtime.startSession("task-1");

		assertEquals(AgentSessionStatus.RUNNING, observed[0],
			"session must be RUNNING while the graph executes");
		assertEquals(AgentSessionStatus.COMPLETED, session.getStatus());
		assertEvent(EventType.SESSION_STARTED);
		assertEvent(EventType.SESSION_COMPLETED);
	}

	@Test
	void pauseResumeKeepsTheSessionAndCompletesOnResume() {
		task("task-1");
		seed("session-1", "task-1", AgentSessionStatus.RUNNING);
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));

		AgentSession paused = runtime.pauseSession("session-1");
		assertEquals(AgentSessionStatus.PAUSED, paused.getStatus());

		AgentSession resumed = runtime.resumeSession("session-1");
		assertEquals(AgentSessionStatus.COMPLETED, resumed.getStatus());
		assertEquals("TEST_AGENT_VERIFY", resumed.getCurrentNodeId());
		assertEvent(EventType.SESSION_PAUSED);
		assertEvent(EventType.SESSION_RESUMED);
		assertEvent(EventType.SESSION_COMPLETED);
	}

	@Test
	void stopTerminatesTheSession() {
		task("task-1");
		seed("session-1", "task-1", AgentSessionStatus.PAUSED);
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));

		AgentSession stopped = runtime.stopSession("session-1");

		assertEquals(AgentSessionStatus.STOPPED, stopped.getStatus());
		assertEvent(EventType.SESSION_STOPPED);
	}

	private AgentExecutionResult completed(AgentExecutionContext context) {
		return AgentExecutionResult.of(context, ExecutionNodeStatus.COMPLETED, "ok", null);
	}

	private void assertEvent(EventType type) {
		assertTrue(events().stream().anyMatch(event -> event.type() == type),
			"missing audit event " + type);
	}
}
