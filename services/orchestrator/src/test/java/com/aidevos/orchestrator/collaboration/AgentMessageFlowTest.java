package com.aidevos.orchestrator.collaboration;

import java.util.List;

import com.aidevos.orchestrator.audit.EventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Message flow verification: every agent message type can be recorded, is
 * stored in team order and is audited with the team/from/to/messageType
 * metadata and the taskId so it appears on the task timeline.
 */
class AgentMessageFlowTest extends CollaborationTestBase {

	@Test
	void messageFlowSupportsAllTypesInOrder() {
		task("task-1");
		AgentTeam team = collaborationService.createTeam("task-1", "session-1");

		for (AgentMessageType type : AgentMessageType.values()) {
			AgentMessage message = collaborationService.sendMessage(team.getTeamId(),
				"HERMES", "CODEX", type, "hello from HERMES");
			assertEquals(type, message.getMessageType());
			assertEquals("HERMES", message.getFromAgent());
			assertEquals("CODEX", message.getToAgent());
			assertNotNull(message.getMessageId());
			assertNotNull(message.getCreatedAt());
		}

		List<AgentMessage> messages = collaborationService.messages(team.getTeamId());
		assertEquals(AgentMessageType.values().length, messages.size());
		assertEquals(List.of(AgentMessageType.REQUEST, AgentMessageType.RESPONSE,
			AgentMessageType.HANDOFF, AgentMessageType.RESULT, AgentMessageType.ERROR,
			AgentMessageType.HUMAN_REQUEST, AgentMessageType.HUMAN_RESPONSE),
			messages.stream().map(AgentMessage::getMessageType).toList());

		var event = lastEvent(EventType.AGENT_MESSAGE_SENT);
		assertEquals(team.getTeamId(), event.metadata().get("teamId"));
		assertEquals("HERMES", event.metadata().get("fromAgent"));
		assertEquals("CODEX", event.metadata().get("toAgent"));
		assertEquals(messages.getLast().getMessageType().name(),
			event.metadata().get("messageType"));
		assertEquals("task-1", event.taskId());
	}

	@Test
	void sendMessageRejectsUnknownTeam() {
		assertThrows(IllegalArgumentException.class, () ->
			collaborationService.sendMessage("team-nope", "HERMES", "CODEX",
				AgentMessageType.REQUEST, "hello"));
	}

	@Test
	void sendMessageAcceptsMessageObject() {
		task("task-1");
		AgentTeam team = collaborationService.createTeam("task-1", "session-1");

		AgentMessage sent = collaborationService.sendMessage(team.getTeamId(),
			new AgentMessage("msg-custom", team.getTeamId(), "A", "B",
				AgentMessageType.RESPONSE, "ack", null));

		assertEquals("msg-custom", sent.getMessageId());
		assertEquals(AgentMessageType.RESPONSE, sent.getMessageType());
	}
}
