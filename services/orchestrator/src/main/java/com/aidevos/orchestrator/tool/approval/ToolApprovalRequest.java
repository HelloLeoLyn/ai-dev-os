package com.aidevos.orchestrator.tool.approval;

import java.time.Instant;

import com.aidevos.orchestrator.approval.ApprovalStatus;

public class ToolApprovalRequest {

	private final String id;
	private final String executionId;
	private final String invocationId;
	private final String jobId;
	private final String providerId;
	private final String toolName;
	private final String argumentsHash;
	private final String workspace;
	private final String permissionLevel;
	private final String reason;
	private final Instant createdAt;
	private ApprovalStatus status = ApprovalStatus.PENDING;
	private Instant decidedAt;

	public ToolApprovalRequest(String id, String executionId, String invocationId, String jobId,
			String providerId, String toolName, String argumentsHash, String workspace,
			String permissionLevel, String reason) {
		this.id = id;
		this.executionId = executionId;
		this.invocationId = invocationId;
		this.jobId = jobId;
		this.providerId = providerId;
		this.toolName = toolName;
		this.argumentsHash = argumentsHash;
		this.workspace = workspace;
		this.permissionLevel = permissionLevel;
		this.reason = reason;
		this.createdAt = Instant.now();
	}

	public static ToolApprovalRequest restore(String id, String executionId, String invocationId,
			String jobId, String providerId, String toolName, String argumentsHash,
			String workspace, String permissionLevel, String reason, Instant createdAt,
			ApprovalStatus status, Instant decidedAt) {
		return new ToolApprovalRequest(id, executionId, invocationId, jobId, providerId,
			toolName, argumentsHash, workspace, permissionLevel, reason, createdAt, status, decidedAt);
	}

	private ToolApprovalRequest(String id, String executionId, String invocationId, String jobId,
			String providerId, String toolName, String argumentsHash, String workspace,
			String permissionLevel, String reason, Instant createdAt, ApprovalStatus status,
			Instant decidedAt) {
		this.id=id; this.executionId=executionId; this.invocationId=invocationId; this.jobId=jobId;
		this.providerId=providerId; this.toolName=toolName; this.argumentsHash=argumentsHash;
		this.workspace=workspace; this.permissionLevel=permissionLevel; this.reason=reason;
		this.createdAt=createdAt; this.status=status; this.decidedAt=decidedAt;
	}

	public synchronized void approve() {
		if (status == ApprovalStatus.PENDING) {
			status = ApprovalStatus.APPROVED;
			decidedAt = Instant.now();
		}
	}

	public synchronized void reject() {
		if (status == ApprovalStatus.PENDING) {
			status = ApprovalStatus.REJECTED;
			decidedAt = Instant.now();
		}
	}

	public synchronized boolean consume() {
		if (status != ApprovalStatus.APPROVED) {
			return false;
		}
		status = ApprovalStatus.CONSUMED;
		return true;
	}

	public String getId() { return id; }
	public String getExecutionId() { return executionId; }
	public String getInvocationId() { return invocationId; }
	public String getJobId() { return jobId; }
	public String getProviderId() { return providerId; }
	public String getToolName() { return toolName; }
	public String getArgumentsHash() { return argumentsHash; }
	public String getWorkspace() { return workspace; }
	public String getPermissionLevel() { return permissionLevel; }
	public String getReason() { return reason; }
	public Instant getCreatedAt() { return createdAt; }
	public synchronized ApprovalStatus getStatus() { return status; }
	public synchronized Instant getDecidedAt() { return decidedAt; }
}
