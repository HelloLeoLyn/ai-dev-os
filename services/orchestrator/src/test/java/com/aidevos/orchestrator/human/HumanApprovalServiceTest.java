package com.aidevos.orchestrator.human;

import java.util.List;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.collaboration.AgentCollaborationService;
import com.aidevos.orchestrator.collaboration.AgentMessageType;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Human approval service verification: request creation, approve resuming
 * the runtime session, reject leaving it paused, terminal-state guards and
 * the task-scoped queries.
 */
class HumanApprovalServiceTest {

	private final InMemoryHumanApprovalRepository approvalRepository =
		new InMemoryHumanApprovalRepository();
	private final InMemoryHumanFeedbackRepository feedbackRepository =
		new InMemoryHumanFeedbackRepository();
	private final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
	private final AuditService auditService = new AuditService(auditRepository);
	private final AgentRuntimeService runtimeService = mock(AgentRuntimeService.class);
	private final AgentCollaborationService collaborationService =
		mock(AgentCollaborationService.class);
	private final HumanCollaborationService service = new HumanCollaborationService(
		approvalRepository, feedbackRepository, auditService, runtimeService,
		collaborationService);

	@Test
	void requestApprovalCreatesPendingApprovalAndAudits() {
		HumanApproval approval = service.requestApproval("task-1", "session-1", "team-1",
			"HUMAN_GATE", "HERMES");

		assertEquals("task-1", approval.getTaskId());
		assertEquals("session-1", approval.getSessionId());
		assertEquals("team-1", approval.getTeamId());
		assertEquals("HUMAN_GATE", approval.getNodeId());
		assertEquals("HERMES", approval.getRequester());
		assertEquals(HumanApprovalStatus.PENDING, approval.getStatus());
		assertNotNull(approval.getCreatedAt());
		var event = lastEvent(EventType.HUMAN_APPROVAL_CREATED);
		assertEquals("task-1", event.taskId());
		assertEquals(approval.getApprovalId(), event.metadata().get("approvalId"));
		assertEquals("session-1", event.metadata().get("sessionId"));
		assertEquals("HERMES", event.metadata().get("agentType"));
	}

	@Test
	void approveMarksApprovedSendsResponseAndResumesSession() {
		HumanApproval approval = service.requestApproval("task-1", "session-1", "team-1",
			"HUMAN_GATE", "HERMES");

		HumanApproval approved = service.approve(approval.getApprovalId(), "alice", "looks good");

		assertEquals(HumanApprovalStatus.APPROVED, approved.getStatus());
		assertEquals("alice", approved.getReviewer());
		assertEquals("looks good", approved.getComment());
		assertNotNull(approved.getReviewedAt());
		verify(runtimeService).resumeSession("session-1");
		verify(collaborationService).sendMessage(eq("team-1"), eq("HUMAN"), eq("HERMES"),
			eq(AgentMessageType.HUMAN_RESPONSE), eq("looks good"));
		assertEvent(EventType.HUMAN_APPROVED);
		assertEvent(EventType.HUMAN_RESUMED);
	}

	@Test
	void rejectMarksRejectedWithoutResumingSession() {
		HumanApproval approval = service.requestApproval("task-1", "session-1", "team-1",
			"HUMAN_GATE", "HERMES");

		HumanApproval rejected = service.reject(approval.getApprovalId(), "alice", "not now");

		assertEquals(HumanApprovalStatus.REJECTED, rejected.getStatus());
		assertEquals("alice", rejected.getReviewer());
		assertEquals("not now", rejected.getComment());
		verify(runtimeService, never()).resumeSession(anyString());
		verify(collaborationService).sendMessage(eq("team-1"), eq("HUMAN"), eq("HERMES"),
			eq(AgentMessageType.HUMAN_RESPONSE), eq("not now"));
		assertEvent(EventType.HUMAN_REJECTED);
	}

	@Test
	void reviewingAnAlreadyReviewedApprovalThrows() {
		HumanApproval approval = service.requestApproval("task-1", "session-1", "team-1",
			"HUMAN_GATE", "HERMES");
		service.approve(approval.getApprovalId(), "alice", "ok");

		assertThrows(IllegalStateException.class,
			() -> service.approve(approval.getApprovalId(), "bob", "again"));
		assertThrows(IllegalStateException.class,
			() -> service.reject(approval.getApprovalId(), "bob", "no"));
	}

	@Test
	void getApprovalAndTaskApprovalsReturnStored() {
		service.requestApproval("task-1", "session-1", "team-1", "HUMAN_GATE", "HERMES");
		service.requestApproval("task-1", "session-1", "team-1", "HUMAN_GATE", "CODEX");
		service.requestApproval("task-2", "session-2", "team-2", "HUMAN_GATE", "HERMES");

		List<HumanApproval> approvals = service.getTaskApprovals("task-1");
		assertEquals(2, approvals.size());
		assertTrue(service.getApproval(approvals.getFirst().getApprovalId()).isPresent());
		assertTrue(service.getApproval("approval-nope").isEmpty());
	}

	private EventRecord lastEvent(EventType type) {
		return events().stream()
			.filter(event -> event.type() == type)
			.reduce((first, second) -> second).orElseThrow();
	}

	private void assertEvent(EventType type) {
		assertTrue(events().stream().anyMatch(event -> event.type() == type),
			"missing audit event " + type);
	}

	private List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
	}
}
