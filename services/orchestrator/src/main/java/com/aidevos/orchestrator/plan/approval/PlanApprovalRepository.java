package com.aidevos.orchestrator.plan.approval;

import java.util.List;

public interface PlanApprovalRepository {
	PlanApprovalRequest save(PlanApprovalRequest request);
	PlanApprovalRequest get(String id);
	List<PlanApprovalRequest> getAll();
	PlanApprovalRequest find(String planId, int planVersion, String requestId, String hash);
}
