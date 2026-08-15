package com.aidevos.orchestrator.approval;

import com.aidevos.orchestrator.persistence.CrudRepository;

public interface CodingApprovalRepository extends CrudRepository<CodingApprovalRequest> {
	CodingApprovalRequest findReusable(String taskId, String jobId, String authority, String operation);
	default CodingApprovalRequest findReusable(String taskId, String jobId) {
		return findReusable(taskId, jobId, "CODING", "WORKSPACE_WRITE");
	}
}
