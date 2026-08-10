package com.aidevos.orchestrator.runtime;

import java.util.List;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.orchestration.AgentExecutionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checkpoint verification: every completed node produces a checkpoint in
 * topology order, a failed node produces a failure checkpoint, and the
 * checkpoint API snapshots the session's current state.
 */
class RuntimeCheckpointTest extends RuntimeTestBase {

	@Test
	void completedNodesProduceCheckpointsInTopologyOrder() {
		task("task-1");
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));

		AgentSession session = runtime.startSession("task-1");

		List<AgentCheckpoint> checkpoints =
			repository.listCheckpoints(session.getSessionId());
		assertEquals(List.of("HERMES_PLANNING", "CODEX_IMPLEMENTATION", "TEST_AGENT_VERIFY"),
			checkpoints.stream().map(AgentCheckpoint::getNodeId).toList());
		for (AgentCheckpoint checkpoint : checkpoints) {
			assertNotNull(checkpoint.getCreatedAt());
			assertNotNull(checkpoint.getExecutionContext());
			assertEquals(checkpoint.getNodeId(),
				checkpoint.getExecutionContext().getNodeId());
		}
		assertEquals(3, events().stream()
			.filter(event -> event.type() == EventType.CHECKPOINT_CREATED
				&& !Boolean.TRUE.equals(event.metadata().get("failed")))
			.count());
	}

	@Test
	void failedNodeProducesFailureCheckpoint() {
		task("task-1");
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			failure(AgentType.CODEX, "code broke"), success(AgentType.TEST_AGENT));

		AgentSession session = runtime.startSession("task-1");

		assertEquals(AgentSessionStatus.FAILED, session.getStatus());
		assertEquals("CODEX_IMPLEMENTATION", session.getCurrentNodeId());
		List<AgentCheckpoint> checkpoints =
			repository.listCheckpoints(session.getSessionId());
		assertEquals(2, checkpoints.size());
		assertEquals("HERMES_PLANNING", checkpoints.get(0).getNodeId());
		assertEquals("CODEX_IMPLEMENTATION", checkpoints.get(1).getNodeId());
		assertTrue(events().stream().anyMatch(event ->
			event.type() == EventType.CHECKPOINT_CREATED
				&& Boolean.TRUE.equals(event.metadata().get("failed"))
				&& "code broke".equals(event.metadata().get("error"))
				&& "CODEX_IMPLEMENTATION".equals(event.metadata().get("nodeId"))));
		assertEvent(EventType.SESSION_FAILED);
	}

	@Test
	void checkpointApiSnapshotsCurrentStateAfterFailure() {
		task("task-1");
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			failure(AgentType.CODEX, "code broke"), success(AgentType.TEST_AGENT));
		AgentSession session = runtime.startSession("task-1");

		AgentCheckpoint snapshot = runtime.checkpoint(session.getSessionId());

		assertEquals(session.getSessionId(), snapshot.getSessionId());
		assertEquals("CODEX_IMPLEMENTATION", snapshot.getNodeId());
		AgentExecutionContext context = snapshot.getExecutionContext();
		assertNotNull(context);
		assertEquals("CODEX_IMPLEMENTATION", context.getNodeId());
		assertEquals(3, repository.listCheckpoints(session.getSessionId()).size());
		assertFalse(Boolean.TRUE.equals(events().stream()
			.filter(event -> event.type() == EventType.CHECKPOINT_CREATED)
			.reduce((first, second) -> second).orElseThrow()
			.metadata().get("failed")));
	}

	private void assertEvent(EventType type) {
		assertTrue(events().stream().anyMatch(event -> event.type() == type),
			"missing audit event " + type);
	}
}
