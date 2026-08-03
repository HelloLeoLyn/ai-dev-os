package com.aidevos.orchestrator.approval;

import java.util.UUID;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.executor.codex.CodexSandbox;
import org.springframework.stereotype.Service;

@Service
public class CodingApprovalService {

	private final CodingApprovalRepository store;
	private final CodingApprovalProperties properties;

	public CodingApprovalService(CodingApprovalRepository store, CodingApprovalProperties properties) {
		this.store = store;
		this.properties = properties;
	}

	public CodingApprovalRequest requireApproval(ExecutionContext context, String workspace,
			CodexSandbox sandbox) {
		if (sandbox != CodexSandbox.WORKSPACE_WRITE || !properties.isRequiredForWorkspaceWrite()) {
			return null;
		}
		CodingApprovalRequest existing = store.findReusable(context.getTaskId(), context.getJobId());
		if (existing != null && existing.consume()) {
			store.save(existing);
			context.getMetadata().put("approvalId", existing.getId());
			return null;
		}
		if (existing != null) {
			context.getMetadata().put("approvalId", existing.getId());
			return existing;
		}
		CodingApprovalRequest request = new CodingApprovalRequest(UUID.randomUUID().toString(),
			context.getTaskId(), context.getJobId(), workspace, sandbox.cliValue(),
			"Coder Agent requests write access to the workspace");
		store.save(request);
		context.getMetadata().put("approvalId", request.getId());
		return request;
	}

	public CodingApprovalRequest approve(String id) {
		CodingApprovalRequest request = store.get(id);
		if (request != null) {
			request.approve();
			store.save(request);
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
}
