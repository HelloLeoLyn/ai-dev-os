package com.aidevos.orchestrator.approval;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.executor.codex.CodexSandbox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CodingApprovalServiceTest {

	@Test
	void shouldRequireAndConsumeWorkspaceWriteApproval() {
		ApprovalStore store = new ApprovalStore();
		CodingApprovalService service = new CodingApprovalService(store, properties(true));
		ExecutionContext context = context("task-1", "job-1");

		CodingApprovalRequest request = service.requireApproval(context, "/workspace",
			CodexSandbox.WORKSPACE_WRITE);

		assertNotNull(request);
		assertEquals(ApprovalStatus.PENDING, request.getStatus());
		assertEquals(request.getId(), context.getMetadata().get("approvalId"));
		service.approve(request.getId());
		assertNull(service.requireApproval(context, "/workspace", CodexSandbox.WORKSPACE_WRITE));
		assertEquals(ApprovalStatus.CONSUMED, request.getStatus());
		assertEquals(request.getId(), context.getMetadata().get("approvalId"));
	}

	@Test
	void shouldNotRequireApprovalForReadOnly() {
		CodingApprovalService service = new CodingApprovalService(new ApprovalStore(), properties(true));

		assertNull(service.requireApproval(context("task-1", null), "/workspace",
			CodexSandbox.READ_ONLY));
	}

	@Test
	void keepsAuthoritiesIndependentAcrossRepeatedResumeRequests() {
		CodingApprovalService service = new CodingApprovalService(new ApprovalStore(), properties(true));
		ExecutionContext context = context("task-1", "job-1");
		CodingApprovalRequest first = service.requireApproval(context, "/workspace",
			CodexSandbox.WORKSPACE_WRITE, "CODING", "WORKSPACE_WRITE");
		assertNotNull(first);
		assertEquals(first.getId(), service.requireApproval(context, "/workspace",
			CodexSandbox.WORKSPACE_WRITE, "CODING", "WORKSPACE_WRITE").getId());
		service.approve(first.getId());
		assertNull(service.requireApproval(context, "/workspace", CodexSandbox.WORKSPACE_WRITE,
			"CODING", "WORKSPACE_WRITE"));
		assertEquals(ApprovalStatus.CONSUMED, first.getStatus());

		CodingApprovalRequest second = service.requireApproval(context, "/workspace",
			CodexSandbox.WORKSPACE_WRITE, "POLICY", "SPECIAL_OPERATION");
		assertNotNull(second);
		assertNotEquals(first.getId(), second.getId());
		assertEquals("POLICY", second.getAuthority());
		assertEquals("SPECIAL_OPERATION", second.getOperation());
		assertEquals(ApprovalStatus.PENDING, second.getStatus());
		service.approve(second.getId());
		assertNull(service.requireApproval(context, "/workspace", CodexSandbox.WORKSPACE_WRITE,
			"POLICY", "SPECIAL_OPERATION"));
		assertEquals(ApprovalStatus.CONSUMED, second.getStatus());
	}

	private CodingApprovalProperties properties(boolean required) {
		CodingApprovalProperties properties = new CodingApprovalProperties();
		properties.setRequiredForWorkspaceWrite(required);
		return properties;
	}

	private ExecutionContext context(String taskId, String jobId) {
		ExecutionContext context = new ExecutionContext();
		context.setTaskId(taskId);
		context.setJobId(jobId);
		return context;
	}
}
