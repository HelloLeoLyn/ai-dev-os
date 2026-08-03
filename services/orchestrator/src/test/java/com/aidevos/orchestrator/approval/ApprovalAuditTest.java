package com.aidevos.orchestrator.approval;

import com.aidevos.orchestrator.audit.*;
import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.executor.codex.CodexSandbox;
import com.aidevos.orchestrator.tool.*;
import com.aidevos.orchestrator.tool.approval.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class ApprovalAuditTest {
	@Test
	void recordsCodingApprovalTransitionsOnce() {
		InMemoryAuditRepository events = new InMemoryAuditRepository();
		CodingApprovalProperties properties = new CodingApprovalProperties();
		properties.setRequiredForWorkspaceWrite(true);
		CodingApprovalService service = new CodingApprovalService(new ApprovalStore(), properties,
			new AuditService(events));
		ExecutionContext context = new ExecutionContext();
		context.setTaskId("task-1"); context.setJobId("job-1");

		CodingApprovalRequest request = service.requireApproval(context, "/private/workspace",
			CodexSandbox.WORKSPACE_WRITE);
		service.approve(request.getId());
		service.approve(request.getId());
		assertNull(service.requireApproval(context, "/private/workspace",
			CodexSandbox.WORKSPACE_WRITE));

		assertEquals(List.of(EventType.CODING_APPROVAL_REQUESTED,
			EventType.CODING_APPROVAL_APPROVED, EventType.CODING_APPROVAL_CONSUMED), types(events));
	}

	@Test
	void recordsToolApprovalTransitionsOnce() {
		InMemoryAuditRepository events = new InMemoryAuditRepository();
		ToolApprovalService service = new ToolApprovalService(new ToolApprovalStore(),
			new ObjectMapper(), new AuditService(events));
		ToolInvocation invocation = new ToolInvocation("execution-1", "invocation-1", "job-1",
			"/private/workspace", "filesystem", "write_file", Map.of("token", "secret"),
			Duration.ofSeconds(1));
		ToolDefinition definition = new ToolDefinition("filesystem", "write_file", "Write",
			Map.of(), ToolAccess.WORKSPACE_WRITE);

		ToolApprovalDecision pending = service.authorize(invocation, definition, "write");
		service.approve(pending.approvalId());
		service.approve(pending.approvalId());
		assertFalse(service.authorize(invocation, definition, "write").approvalRequired());

		assertEquals(List.of(EventType.TOOL_APPROVAL_REQUESTED,
			EventType.TOOL_APPROVAL_APPROVED, EventType.TOOL_APPROVAL_CONSUMED), types(events));
	}

	private List<EventType> types(InMemoryAuditRepository events) {
		return events.query(EventQuery.all()).stream().map(EventRecord::type).toList();
	}
}
