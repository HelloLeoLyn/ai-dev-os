package com.aidevos.orchestrator.human;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.collaboration.AgentMessage;
import com.aidevos.orchestrator.collaboration.AgentMessageType;
import com.aidevos.orchestrator.collaboration.AgentTeam;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.runtime.AgentSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Human feedback verification: feedback is stored, audited with the
 * agentType metadata and delivered to the task's agent team as a
 * HUMAN_RESPONSE message.
 */
class HumanFeedbackTest extends HumanTestBase {

	@Test
	void addFeedbackStoresAuditsAndDeliversToTeam() {
		task("task-1");
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));
		AgentSession session = runtime.startSession("task-1");
		AgentTeam team = collaborationService.teamForTask("task-1").orElseThrow();

		HumanFeedback feedback = humanService.addFeedback("task-1", session.getSessionId(),
			"CODEX", "please add tests");

		assertEquals("CODEX", feedback.getAgentType());
		assertEquals("please add tests", feedback.getContent());
		assertEquals("task-1", feedback.getTaskId());
		assertNotNull(feedback.getCreatedAt());
		assertTrue(collaborationService.messages(team.getTeamId()).stream().anyMatch(message ->
			message.getMessageType() == AgentMessageType.HUMAN_RESPONSE
				&& "HUMAN".equals(message.getFromAgent())
				&& "CODEX".equals(message.getToAgent())
				&& "please add tests".equals(message.getContent())));
		var event = lastEvent(EventType.HUMAN_FEEDBACK_ADDED);
		assertEquals("task-1", event.taskId());
		assertEquals("CODEX", event.metadata().get("agentType"));
		assertEquals(feedback.getFeedbackId(), event.metadata().get("feedbackId"));
		assertEquals(1, humanService.getFeedbacks("task-1").size());
		assertTrue(humanService.getFeedback(feedback.getFeedbackId()).isPresent());
	}

	@Test
	void feedbackDoesNotChangeSessionState() {
		task("task-1");
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));
		AgentSession session = runtime.startSession("task-1");

		humanService.addFeedback("task-1", session.getSessionId(), "TEST_AGENT", "rerun");

		assertEquals(com.aidevos.orchestrator.runtime.AgentSessionStatus.COMPLETED,
			session.getStatus());
		assertTrue(events().stream().noneMatch(event ->
			event.type() == EventType.HUMAN_APPROVAL_CREATED));
	}
}
