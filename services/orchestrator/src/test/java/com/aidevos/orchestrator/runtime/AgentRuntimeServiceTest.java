package com.aidevos.orchestrator.runtime;

import java.util.Optional;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.EventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the AgentRuntimeService contract: starting a session runs
 * the graph and finalizes it, getSession/checkpoint behave, and the
 * pause/resume/stop state transitions are guarded.
 */
class AgentRuntimeServiceTest extends RuntimeTestBase {

	@Test
	void startSessionCreatesRunningSessionRunsGraphAndCompletes() {
		task("task-1");
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));

		AgentSession session = runtime.startSession("task-1");

		assertEquals(AgentSessionStatus.COMPLETED, session.getStatus());
		assertEquals("TEST_AGENT_VERIFY", session.getCurrentNodeId());
		assertEquals(1, repository.listByTask("task-1").size());
		assertEquals(3, repository.listCheckpoints(session.getSessionId()).size());
		assertEvent(EventType.SESSION_STARTED);
		assertEvent(EventType.SESSION_COMPLETED);
		assertEquals(3, events().stream()
			.filter(event -> event.type() == EventType.CHECKPOINT_CREATED).count());
	}

	@Test
	void getSessionReturnsSessionOrEmpty() {
		task("task-1");
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));

		AgentSession session = runtime.startSession("task-1");

		Optional<AgentSession> found = runtime.getSession(session.getSessionId());
		assertTrue(found.isPresent());
		assertEquals(session.getSessionId(), found.orElseThrow().getSessionId());
		assertTrue(runtime.getSession("missing").isEmpty());
	}

	@Test
	void checkpointSnapshotsCurrentNodeAndContext() {
		task("task-1");
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));
		AgentSession session = runtime.startSession("task-1");

		AgentCheckpoint checkpoint = runtime.checkpoint(session.getSessionId());

		assertEquals(session.getSessionId(), checkpoint.getSessionId());
		assertEquals("TEST_AGENT_VERIFY", checkpoint.getNodeId());
		assertEquals(4, repository.listCheckpoints(session.getSessionId()).size());
		assertEvent(EventType.CHECKPOINT_CREATED);
	}

	@Test
	void pauseRequiresRunningSession() {
		task("task-1");
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));
		AgentSession completed = runtime.startSession("task-1");

		assertThrows(IllegalStateException.class,
			() -> runtime.pauseSession(completed.getSessionId()));
	}

	@Test
	void pauseAndResumeTransitionRunningSession() {
		task("task-1");
		seed("session-1", "task-1", AgentSessionStatus.RUNNING);
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));

		AgentSession paused = runtime.pauseSession("session-1");
		assertEquals(AgentSessionStatus.PAUSED, paused.getStatus());
		assertEvent(EventType.SESSION_PAUSED);

		AgentSession resumed = runtime.resumeSession("session-1");
		assertEquals(AgentSessionStatus.COMPLETED, resumed.getStatus());
		assertEvent(EventType.SESSION_RESUMED);
		assertEvent(EventType.SESSION_COMPLETED);
	}

	@Test
	void stopStopsRunningSessionAndPreventsFurtherTransitions() {
		task("task-1");
		seed("session-1", "task-1", AgentSessionStatus.RUNNING);
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));

		AgentSession stopped = runtime.stopSession("session-1");

		assertEquals(AgentSessionStatus.STOPPED, stopped.getStatus());
		assertEvent(EventType.SESSION_STOPPED);
		assertThrows(IllegalStateException.class, () -> runtime.pauseSession("session-1"));
		assertThrows(IllegalStateException.class, () -> runtime.resumeSession("session-1"));
		assertThrows(IllegalStateException.class, () -> runtime.stopSession("session-1"));
	}

	private void assertEvent(EventType type) {
		assertTrue(events().stream().anyMatch(event -> event.type() == type),
			"missing audit event " + type);
	}
}
