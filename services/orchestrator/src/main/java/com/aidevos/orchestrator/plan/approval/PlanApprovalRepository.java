package com.aidevos.orchestrator.plan.approval;

import java.util.List;

public interface PlanApprovalRepository {
	PlanApprovalRequest save(PlanApprovalRequest request);
	PlanApprovalRequest get(String id);
	List<PlanApprovalRequest> getAll();
	PlanApprovalRequest find(String planId, int planVersion, String requestId, String hash);
	/**
	 * Atomically transitions an APPROVED plan approval to CONSUMED. Returns
	 * false when the request is missing or is not in APPROVED state, so the
	 * consume is executed exactly once under concurrent schedulers.
	 */
	boolean consumeIfApproved(String id);
}
