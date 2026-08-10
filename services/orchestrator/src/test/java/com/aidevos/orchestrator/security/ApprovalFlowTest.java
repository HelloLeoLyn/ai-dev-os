package com.aidevos.orchestrator.security;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 17-A: dangerous permission approval flow (PENDING -> APPROVED /
 * REJECTED). Approvals are never granted automatically.
 */
class ApprovalFlowTest {

	private ApprovalService service;

	@BeforeEach
	void setUp() {
		service = new ApprovalService(new AuditService(new InMemoryAuditRepository()));
	}

	@Test
	void shouldCreatePendingApproval() {
		ApprovalRequest approval = service.request("task-1", AgentType.TEST_AGENT,
			SecurityPermission.EXECUTE_COMMAND, "run test command");

		assertEquals(ApprovalRequest.ApprovalStatus.PENDING, approval.getStatus());
		assertFalse(service.isApproved("task-1", SecurityPermission.EXECUTE_COMMAND));
	}

	@Test
	void shouldApproveRequest() {
		ApprovalRequest approval = service.request("task-1", AgentType.TEST_AGENT,
			SecurityPermission.EXECUTE_COMMAND, "run test command");

		service.approve(approval.getRequestId());

		assertEquals(ApprovalRequest.ApprovalStatus.APPROVED, approval.getStatus());
		assertTrue(service.isApproved("task-1", SecurityPermission.EXECUTE_COMMAND));
	}

	@Test
	void shouldRejectRequest() {
		ApprovalRequest approval = service.request("task-1", AgentType.TEST_AGENT,
			SecurityPermission.EXECUTE_COMMAND, "run test command");

		service.reject(approval.getRequestId());

		assertEquals(ApprovalRequest.ApprovalStatus.REJECTED, approval.getStatus());
		assertFalse(service.isApproved("task-1", SecurityPermission.EXECUTE_COMMAND));
	}

	@Test
	void shouldNotAllowDecidingAnAlreadyDecidedRequest() {
		ApprovalRequest approval = service.request("task-1", AgentType.TEST_AGENT,
			SecurityPermission.EXECUTE_COMMAND, "run test command");
		service.approve(approval.getRequestId());

		assertThrows(IllegalStateException.class,
			() -> service.approve(approval.getRequestId()));
		assertThrows(IllegalStateException.class,
			() -> service.reject(approval.getRequestId()));
	}

	@Test
	void shouldReturnEmptyForUnknownRequest() {
		assertTrue(service.approve("missing").isEmpty());
		assertTrue(service.reject("missing").isEmpty());
	}
}
