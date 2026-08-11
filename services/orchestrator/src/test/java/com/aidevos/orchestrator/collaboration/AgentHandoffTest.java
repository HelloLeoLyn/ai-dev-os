package com.aidevos.orchestrator.collaboration;

import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.memory.MemoryContext;
import com.aidevos.orchestrator.memory.MemoryType;
import com.aidevos.orchestrator.memory.search.MemoryMatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Handoff verification: a handoff records the from/to agents, carries the
 * project memory context (similar tasks / solutions / warnings) to the
 * receiving agent, marks the team WAITING and audits AGENT_HANDOFF.
 */
class AgentHandoffTest extends CollaborationTestBase {

	@Test
	void handoffPassesMemoryContextToReceivingAgent() {
		task("task-1");
		AgentTeam team = collaborationService.createTeam("task-1", "session-1");
		MemoryContext memory = new MemoryContext(
			List.of(new MemoryMatch("mem-1", MemoryType.HISTORY_TASK, 0.9,
				"similar task", "solution-a", Map.of())),
			List.of(new MemoryMatch("mem-2", MemoryType.AGENT_EXPERIENCE, 0.8,
				"known fix", "apply patch", Map.of())),
			List.of("watch out for flaky tests"),
			List.of("keep tests green"));

		AgentMessage message = collaborationService.handoff(team.getTeamId(),
			"HERMES", "CODEX", memory);

		assertEquals(AgentMessageType.HANDOFF, message.getMessageType());
		assertEquals("HERMES", message.getFromAgent());
		assertEquals("CODEX", message.getToAgent());
		assertTrue(message.getContent().contains("similarTasks"));
		assertTrue(message.getContent().contains("flaky tests"));
		assertEquals(AgentTeamStatus.WAITING, team.getStatus());

		var event = lastEvent(EventType.AGENT_HANDOFF);
		assertEquals(team.getTeamId(), event.metadata().get("teamId"));
		assertEquals("HERMES", event.metadata().get("fromAgent"));
		assertEquals("CODEX", event.metadata().get("toAgent"));
		assertEquals("HANDOFF", event.metadata().get("messageType"));
		@SuppressWarnings("unchecked")
		Map<String, Object> memorySummary = (Map<String, Object>) event.metadata().get("memory");
		assertEquals(List.of("mem-1"), memorySummary.get("similarTasks"));
		assertEquals(List.of("mem-2"), memorySummary.get("solutions"));
		assertEquals(List.of("watch out for flaky tests"), memorySummary.get("warnings"));
		assertEquals("task-1", event.taskId());

		assertEquals(List.of("HERMES->CODEX"),
			collaborationService.handoffs(team.getTeamId()));
	}

	@Test
	void handoffWithoutMemoryContextStillRecordsTrail() {
		task("task-1");
		AgentTeam team = collaborationService.createTeam("task-1", "session-1");

		collaborationService.handoff(team.getTeamId(), "CODEX", "TEST_AGENT", null);

		assertEquals(List.of("CODEX->TEST_AGENT"),
			collaborationService.handoffs(team.getTeamId()));
		assertEquals(AgentTeamStatus.WAITING, team.getStatus());
	}
}
