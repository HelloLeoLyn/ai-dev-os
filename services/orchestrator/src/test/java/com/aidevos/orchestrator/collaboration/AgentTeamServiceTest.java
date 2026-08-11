package com.aidevos.orchestrator.collaboration;

import java.util.List;

import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Team lifecycle verification: creation, agent joins, completion writing the
 * AGENT_EXPERIENCE memory record and the failure path.
 */
class AgentTeamServiceTest extends CollaborationTestBase {

	@Test
	void createTeamCreatesRunningTeamAndIsReusablePerSession() {
		task("task-1");

		AgentTeam team = collaborationService.createTeam("task-1", "session-1");

		assertEquals("task-1", team.getTaskId());
		assertEquals("session-1", team.getSessionId());
		assertEquals(AgentTeamStatus.RUNNING, team.getStatus());
		assertEvent(EventType.AGENT_TEAM_CREATED);
		assertSame(team, collaborationService.createTeam("task-1", "session-1"),
			"resumed sessions must reuse the same team");
	}

	@Test
	void addAgentJoinsAgentsInOrderAndAudits() {
		task("task-1");
		AgentTeam team = collaborationService.createTeam("task-1", "session-1");

		collaborationService.addAgent(team.getTeamId(), "HERMES");
		collaborationService.addAgent(team.getTeamId(), "CODEX");
		collaborationService.addAgent(team.getTeamId(), "CODEX");

		assertEquals(List.of("HERMES", "CODEX"), team.getAgents());
		assertEquals(AgentTeamStatus.RUNNING, team.getStatus());
		assertEquals(2, events().stream()
			.filter(event -> event.type() == EventType.AGENT_JOINED_TEAM).count());
	}

	@Test
	void completeTeamWritesAgentExperienceAndAudits() {
		task("task-1");
		AgentTeam team = collaborationService.createTeam("task-1", "session-1");
		collaborationService.addAgent(team.getTeamId(), "HERMES");
		collaborationService.addAgent(team.getTeamId(), "CODEX");
		collaborationService.sendMessage(team.getTeamId(), "HERMES", "CODEX",
			AgentMessageType.RESULT, "plan ok");
		collaborationService.handoff(team.getTeamId(), "HERMES", "CODEX", null);

		AgentTeam completed = collaborationService.completeTeam(team.getTeamId());

		assertEquals(AgentTeamStatus.COMPLETED, completed.getStatus());
		assertEvent(EventType.AGENT_COLLABORATION_COMPLETED);
		List<MemoryRecord> experiences =
			memoryRepository.list("project-x", MemoryType.AGENT_EXPERIENCE);
		assertEquals(1, experiences.size());
		assertTrue(experiences.getFirst().getContent().contains("HERMES"));
		assertTrue(experiences.getFirst().getContent().contains("HERMES->CODEX"));
	}

	@Test
	void failTeamMarksFailedAndAudits() {
		task("task-1");
		AgentTeam team = collaborationService.createTeam("task-1", "session-1");

		AgentTeam failed = collaborationService.failTeam(team.getTeamId(), "code broke");

		assertEquals(AgentTeamStatus.FAILED, failed.getStatus());
		assertEvent(EventType.AGENT_COLLABORATION_FAILED);
		assertEquals("code broke", lastEvent(EventType.AGENT_COLLABORATION_FAILED)
			.metadata().get("error"));
	}

	@Test
	void getTeamReturnsTeamOrEmpty() {
		task("task-1");
		AgentTeam team = collaborationService.createTeam("task-1", "session-1");

		assertEquals(team.getTeamId(),
			collaborationService.getTeam(team.getTeamId()).orElseThrow().getTeamId());
		assertTrue(collaborationService.getTeam("team-nope").isEmpty());
	}
}
