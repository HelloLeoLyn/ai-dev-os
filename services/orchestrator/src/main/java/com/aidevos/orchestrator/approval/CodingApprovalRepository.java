package com.aidevos.orchestrator.approval;

import com.aidevos.orchestrator.persistence.CrudRepository;

public interface CodingApprovalRepository extends CrudRepository<CodingApprovalRequest> {
	CodingApprovalRequest findReusable(String taskId, String jobId);
}
