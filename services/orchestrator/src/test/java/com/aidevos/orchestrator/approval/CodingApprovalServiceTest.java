package com.aidevos.orchestrator.approval;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.executor.codex.CodexSandbox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
