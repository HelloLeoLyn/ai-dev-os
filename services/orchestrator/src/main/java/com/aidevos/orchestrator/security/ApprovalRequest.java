package com.aidevos.orchestrator.security;

import java.time.Instant;

import com.aidevos.orchestrator.agent.AgentType;

/**
 * Human approval request for a dangerous permission. The flow is PENDING ->
 * APPROVED / REJECTED; approvals are per task and permission and are never
 * granted automatically.
 */
public class ApprovalRequest {

	public enum ApprovalStatus {
		PENDING,
		APPROVED,
		REJECTED
	}

	private final String requestId;
	private final String taskId;
	private final AgentType agentType;
	private final SecurityPermission permission;
	private final String reason;
	private volatile ApprovalStatus status;
	private volatile Instant decidedAt;

	public ApprovalRequest(String requestId, String taskId, AgentType agentType,
			SecurityPermission permission, String reason) {
		if (requestId == null || requestId.isBlank()) {
			throw new IllegalArgumentException("Request id is required");
		}
		if (taskId == null || taskId.isBlank()) {
			throw new IllegalArgumentException("Task id is required");
		}
		if (permission == null) {
			throw new IllegalArgumentException("Permission is required");
		}
		this.requestId = requestId;
		this.taskId = taskId;
		this.agentType = agentType;
		this.permission = permission;
		this.reason = reason;
		this.status = ApprovalStatus.PENDING;
	}

	public String getRequestId() {
		return requestId;
	}

	public String getTaskId() {
		return taskId;
	}

	public AgentType getAgentType() {
		return agentType;
	}

	public SecurityPermission getPermission() {
		return permission;
	}

	public String getReason() {
		return reason;
	}

	public ApprovalStatus getStatus() {
		return status;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public boolean isApproved() {
		return status == ApprovalStatus.APPROVED;
	}

	public void approve() {
		if (status != ApprovalStatus.PENDING) {
			throw new IllegalStateException("Approval request is not pending: " + requestId);
		}
		status = ApprovalStatus.APPROVED;
		decidedAt = Instant.now();
	}

	public void reject() {
		if (status != ApprovalStatus.PENDING) {
			throw new IllegalStateException("Approval request is not pending: " + requestId);
		}
		status = ApprovalStatus.REJECTED;
		decidedAt = Instant.now();
	}
}
