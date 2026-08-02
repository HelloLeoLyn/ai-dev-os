package com.aidevos.orchestrator.tool.approval;

import java.time.Duration;
import java.util.Map;

import com.aidevos.orchestrator.approval.ApprovalStatus;
import com.aidevos.orchestrator.tool.ToolAccess;
import com.aidevos.orchestrator.tool.ToolDefinition;
import com.aidevos.orchestrator.tool.ToolInvocation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolApprovalServiceTest {

	@Test
	void shouldBindAuditFieldsAndConsumeApprovedRequest() {
		ToolApprovalStore store = new ToolApprovalStore();
		ToolApprovalService service = new ToolApprovalService(store, new ObjectMapper());
		ToolInvocation invocation = invocation(Map.of("path", "a.txt", "content", "x"));
		ToolDefinition definition = definition();

		ToolApprovalDecision pending = service.authorize(invocation, definition, "write requested");
		ToolApprovalRequest request = store.get(pending.approvalId());
		service.approve(request.getId());
		ToolApprovalDecision approved = service.authorize(invocation, definition, "write requested");

		assertTrue(pending.approvalRequired());
		assertFalse(approved.approvalRequired());
		assertEquals(request.getId(), approved.approvalId());
		assertEquals(ApprovalStatus.CONSUMED, request.getStatus());
		assertEquals("execution-1", request.getExecutionId());
		assertEquals("invocation-1", request.getInvocationId());
		assertEquals("filesystem", request.getProviderId());
		assertEquals("write_file", request.getToolName());
		assertEquals("/workspace", request.getWorkspace());
		assertEquals("workspace-write", request.getPermissionLevel());
		assertEquals(64, request.getArgumentsHash().length());
	}

	@Test
	void shouldCreateNewApprovalWhenArgumentsChange() {
		ToolApprovalService service = new ToolApprovalService(new ToolApprovalStore(),
			new ObjectMapper());
		ToolApprovalDecision first = service.authorize(invocation(Map.of("content", "one")),
			definition(), "write requested");
		service.approve(first.approvalId());

		ToolApprovalDecision changed = service.authorize(invocation(Map.of("content", "two")),
			definition(), "write requested");

		assertTrue(changed.approvalRequired());
		assertNotEquals(first.approvalId(), changed.approvalId());
	}

	private ToolInvocation invocation(Map<String, Object> arguments) {
		return new ToolInvocation("execution-1", "invocation-1", "job-1", "/workspace",
			"filesystem", "write_file", arguments, Duration.ofSeconds(1));
	}

	private ToolDefinition definition() {
		return new ToolDefinition("filesystem", "write_file", "Write", Map.of(),
			ToolAccess.WORKSPACE_WRITE);
	}
}
