package com.aidevos.orchestrator.plan.approval;

import java.time.Instant;

import com.aidevos.orchestrator.approval.ApprovalStatus;
import com.aidevos.orchestrator.plan.Plan;

public class PlanApprovalRequest {

	private final String id;
	private final String planId;
	private final int planVersion;
	private final String requestId;
	private final String planSnapshotHash;
	private final Plan plan;
	private final Instant createdAt;
	private ApprovalStatus status;
	private ApprovalStatus decision;
	private Instant decidedAt;
	private String approver;
	private String rejectionReason;

	public PlanApprovalRequest(String id, String requestId, Plan plan,
			String planSnapshotHash, Instant createdAt) {
		this.id = id;
		this.planId = plan.id();
		this.planVersion = plan.version();
		this.requestId = requestId;
		this.planSnapshotHash = planSnapshotHash;
		this.plan = plan;
		this.createdAt = createdAt;
		this.status = ApprovalStatus.PENDING;
	}

	public synchronized void approve(String decidedBy, Instant decisionTime) {
		requirePending();
		approver = requireText(decidedBy, "Approver is required");
		decision = ApprovalStatus.APPROVED;
		status = ApprovalStatus.APPROVED;
		decidedAt = decisionTime;
	}

	public synchronized void reject(String decidedBy, String reason, Instant decisionTime) {
		requirePending();
		approver = requireText(decidedBy, "Approver is required");
		rejectionReason = requireText(reason, "Rejection reason is required");
		decision = ApprovalStatus.REJECTED;
		status = ApprovalStatus.REJECTED;
		decidedAt = decisionTime;
	}

	public synchronized void consume() {
		if (status != ApprovalStatus.APPROVED) {
			throw new IllegalStateException("Only approved plans can be consumed");
		}
		status = ApprovalStatus.CONSUMED;
	}

	private void requirePending() {
		if (status != ApprovalStatus.PENDING) {
			throw new IllegalStateException("Plan approval has already been decided");
		}
	}

	private String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	public String getId() { return id; }
	public String getPlanId() { return planId; }
	public int getPlanVersion() { return planVersion; }
	public String getRequestId() { return requestId; }
	public String getPlanSnapshotHash() { return planSnapshotHash; }
	public Plan getPlan() { return plan; }
	public Instant getCreatedAt() { return createdAt; }
	public synchronized ApprovalStatus getStatus() { return status; }
	public synchronized ApprovalStatus getDecision() { return decision; }
	public synchronized Instant getDecidedAt() { return decidedAt; }
	public synchronized String getApprover() { return approver; }
	public synchronized String getRejectionReason() { return rejectionReason; }
}
