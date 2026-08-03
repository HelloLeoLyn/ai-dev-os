package com.aidevos.orchestrator.approval;

import java.time.Instant;

public class CodingApprovalRequest {

	private final String id;
	private final String taskId;
	private final String jobId;
	private final String workspace;
	private final String sandbox;
	private final String reason;
	private final Instant createdAt;
	private ApprovalStatus status;
	private Instant decidedAt;

	public CodingApprovalRequest(String id, String taskId, String jobId, String workspace,
			String sandbox, String reason) {
		this.id = id;
		this.taskId = taskId;
		this.jobId = jobId;
		this.workspace = workspace;
		this.sandbox = sandbox;
		this.reason = reason;
		this.createdAt = Instant.now();
		this.status = ApprovalStatus.PENDING;
	}

	public static CodingApprovalRequest restore(String id, String taskId, String jobId,
			String workspace, String sandbox, String reason, Instant createdAt,
			ApprovalStatus status, Instant decidedAt) {
		return new CodingApprovalRequest(id, taskId, jobId, workspace, sandbox, reason,
			createdAt, status, decidedAt);
	}

	private CodingApprovalRequest(String id, String taskId, String jobId, String workspace,
			String sandbox, String reason, Instant createdAt, ApprovalStatus status,
			Instant decidedAt) {
		this.id=id; this.taskId=taskId; this.jobId=jobId; this.workspace=workspace;
		this.sandbox=sandbox; this.reason=reason; this.createdAt=createdAt;
		this.status=status; this.decidedAt=decidedAt;
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
	public String getTaskId() { return taskId; }
	public String getJobId() { return jobId; }
	public String getWorkspace() { return workspace; }
	public String getSandbox() { return sandbox; }
	public String getReason() { return reason; }
	public Instant getCreatedAt() { return createdAt; }
	public synchronized ApprovalStatus getStatus() { return status; }
	public synchronized Instant getDecidedAt() { return decidedAt; }
}
