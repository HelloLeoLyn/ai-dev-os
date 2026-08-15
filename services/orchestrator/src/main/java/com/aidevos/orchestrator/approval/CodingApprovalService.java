package com.aidevos.orchestrator.approval;

import java.util.UUID;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.executor.codex.CodexSandbox;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CodingApprovalService {

	private final CodingApprovalRepository store;
	private final CodingApprovalProperties properties;
	private final AuditService auditService;

	public CodingApprovalService(CodingApprovalRepository store, CodingApprovalProperties properties) {
		this(store, properties, AuditService.noop());
	}

	@Autowired
	public CodingApprovalService(CodingApprovalRepository store, CodingApprovalProperties properties,
			AuditService auditService) {
		this.store = store;
		this.properties = properties;
		this.auditService = auditService;
	}

	public CodingApprovalRequest requireApproval(ExecutionContext context, String workspace,
			CodexSandbox sandbox) {
		return requireApproval(context, workspace, sandbox, "CODING", "WORKSPACE_WRITE");
	}

	public CodingApprovalRequest requireApproval(ExecutionContext context, String workspace,
			CodexSandbox sandbox, String authority, String operation) {
		if (sandbox != CodexSandbox.WORKSPACE_WRITE || !properties.isRequiredForWorkspaceWrite()) {
			return null;
		}
		CodingApprovalRequest existing = store.findReusable(context.getTaskId(), context.getJobId(), authority, operation);
		if (existing != null && existing.consume()) {
			store.save(existing);
			auditService.codingApprovalEvent(EventType.CODING_APPROVAL_CONSUMED, existing,
				ApprovalStatus.APPROVED.name(), existing.getStatus().name());
			context.getMetadata().put("approvalId", existing.getId());
			return null;
		}
		if (existing != null) {
			context.getMetadata().put("approvalId", existing.getId());
			return existing;
		}
		CodingApprovalRequest request = new CodingApprovalRequest(UUID.randomUUID().toString(),
			context.getTaskId(), context.getJobId(), workspace, sandbox.cliValue(),
			"Coder Agent requests write access to the workspace", authority, operation);
		store.save(request);
		auditService.codingApprovalEvent(EventType.CODING_APPROVAL_REQUESTED, request, null,
			request.getStatus().name());
		context.getMetadata().put("approvalId", request.getId());
		return request;
	}

	public CodingApprovalRequest approve(String id) {
		CodingApprovalRequest request = store.get(id);
		if (request != null) {
			ApprovalStatus before = request.getStatus();
			request.approve();
			store.save(request);
			if (before != request.getStatus()) {
				auditService.codingApprovalEvent(EventType.CODING_APPROVAL_APPROVED, request,
					before.name(), request.getStatus().name());
			}
		}
		return request;
	}

	public CodingApprovalRequest reject(String id) {
		CodingApprovalRequest request = store.get(id);
		if (request != null) {
			request.reject();
			store.save(request);
		}
		return request;
	}

	public java.util.List<CodingApprovalRequest> getAll() {
		return store.getAll();
	}

	public CodingApprovalRequest get(String id) {
		return store.get(id);
	}
}
