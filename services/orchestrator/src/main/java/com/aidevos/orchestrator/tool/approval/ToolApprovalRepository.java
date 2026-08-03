package com.aidevos.orchestrator.tool.approval;

import com.aidevos.orchestrator.persistence.CrudRepository;

public interface ToolApprovalRepository extends CrudRepository<ToolApprovalRequest> {
	ToolApprovalRequest findReusable(String jobId, String invocationId, String providerId,
		String toolName, String argumentsHash, String workspace, String permissionLevel);
}
