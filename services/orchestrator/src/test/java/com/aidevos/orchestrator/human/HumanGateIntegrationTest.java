package com.aidevos.orchestrator.human;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.collaboration.AgentMessage;
import com.aidevos.orchestrator.collaboration.AgentMessageType;
import com.aidevos.orchestrator.collaboration.AgentTeam;
import com.aidevos.orchestrator.orchestration.ExecutionGraph;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.runtime.AgentSession;
import com.aidevos.orchestrator.runtime.AgentSessionStatus;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration verification of the human gate: execution pauses at the gate
 * node with a PENDING approval, the runtime session is PAUSED, and an
 * approved request resumes the session so the downstream agents continue to
 * completion; a rejected request keeps the session paused.
 */
class HumanGateIntegrationTest extends HumanTestBase {

	@Test
	void gatePausesSessionThenApprovalResumesAndCompletes() {
		TaskRecord taskRecord = task("task-1");
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));
		ExecutionGraph graph = graphBuilder.codeGraphWithHumanGate("task-1");
		graphExecutor.execute(graph, baseContext(graph, taskRecord));

		AgentSession session = sessionRepository.listByTask("task-1").getFirst();
		assertEquals(AgentSessionStatus.PAUSED, session.getStatus());
		assertEquals("HUMAN_GATE", session.getCurrentNodeId());
		HumanApproval approval = humanService.getTaskApprovals("task-1").getFirst();
		assertEquals(HumanApprovalStatus.PENDING, approval.getStatus());
		assertEquals("HERMES", approval.getRequester());
		assertEquals("HUMAN_GATE", approval.getNodeId());
		assertEvent(EventType.HUMAN_APPROVAL_CREATED);
		assertEvent(EventType.SESSION_PAUSED);
		AgentTeam team = collaborationService.teamForTask("task-1").orElseThrow();
		assertTrue(collaborationService.messages(team.getTeamId()).stream().anyMatch(message ->
			message.getMessageType() == AgentMessageType.HUMAN_REQUEST
				&& "HERMES".equals(message.getFromAgent())
				&& "HUMAN".equals(message.getToAgent())));

		HumanApproval approved = humanService.approve(approval.getApprovalId(),
			"alice", "ok");

		assertEquals(HumanApprovalStatus.APPROVED, approved.getStatus());
		assertEquals("alice", approved.getReviewer());
		assertEquals("ok", approved.getComment());
		assertTrue(approved.getReviewedAt() != null);
		assertEquals(AgentSessionStatus.COMPLETED,
			sessionRepository.get(session.getSessionId()).getStatus());
		assertEquals("TEST_AGENT_VERIFY",
			sessionRepository.get(session.getSessionId()).getCurrentNodeId());
		assertEvent(EventType.HUMAN_APPROVED);
		assertEvent(EventType.HUMAN_RESUMED);
		assertEvent(EventType.SESSION_COMPLETED);
		assertEvent(EventType.AGENT_COLLABORATION_COMPLETED);
		assertTrue(collaborationService.messages(team.getTeamId()).stream().anyMatch(message ->
			message.getMessageType() == AgentMessageType.HUMAN_RESPONSE
				&& "HUMAN".equals(message.getFromAgent())
				&& "HERMES".equals(message.getToAgent())
				&& "ok".equals(message.getContent())));
	}

	@Test
	void rejectedApprovalKeepsSessionPaused() {
		TaskRecord taskRecord = task("task-1");
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));
		ExecutionGraph graph = graphBuilder.codeGraphWithHumanGate("task-1");
		graphExecutor.execute(graph, baseContext(graph, taskRecord));
		AgentSession session = sessionRepository.listByTask("task-1").getFirst();
		HumanApproval approval = humanService.getTaskApprovals("task-1").getFirst();

		HumanApproval rejected = humanService.reject(approval.getApprovalId(),
			"alice", "not now");

		assertEquals(HumanApprovalStatus.REJECTED, rejected.getStatus());
		assertEquals(AgentSessionStatus.PAUSED, session.getStatus());
		assertEquals("HUMAN_GATE", session.getCurrentNodeId());
		assertEvent(EventType.HUMAN_REJECTED);
		assertFalse(events().stream().anyMatch(event ->
			event.type() == EventType.HUMAN_RESUMED));
	}
}
