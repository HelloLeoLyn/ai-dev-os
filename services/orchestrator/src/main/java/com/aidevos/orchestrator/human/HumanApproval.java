package com.aidevos.orchestrator.human;

import java.time.Instant;

/**
 * One human-in-the-loop approval: the runtime session and graph node that
 * wait for a human decision, the requesting agent and the reviewer. The
 * session stays PAUSED until the approval is APPROVED, which resumes the
 * runtime session.
 */
public class HumanApproval {

	private final String approvalId;
	private final String taskId;
	private final String sessionId;
	private final String teamId;
	private final String nodeId;
	private volatile HumanApprovalStatus status;
	private final String requester;
	private volatile String reviewer;
	private volatile String comment;
	private final Instant createdAt;
	private volatile Instant reviewedAt;

	public HumanApproval(String approvalId, String taskId, String sessionId, String teamId,
			String nodeId, HumanApprovalStatus status, String requester, String reviewer,
			String comment, Instant createdAt, Instant reviewedAt) {
		this.approvalId = approvalId;
		this.taskId = taskId;
		this.sessionId = sessionId;
		this.teamId = teamId;
		this.nodeId = nodeId;
		this.status = status == null ? HumanApprovalStatus.PENDING : status;
		this.requester = requester == null ? "" : requester;
		this.reviewer = reviewer;
		this.comment = comment;
		this.createdAt = createdAt == null ? Instant.now() : createdAt;
		this.reviewedAt = reviewedAt;
	}

	public synchronized void approve(String reviewer, String comment) {
		this.status = HumanApprovalStatus.APPROVED;
		this.reviewer = reviewer;
		this.comment = comment;
		this.reviewedAt = Instant.now();
	}

	public synchronized void reject(String reviewer, String comment) {
		this.status = HumanApprovalStatus.REJECTED;
		this.reviewer = reviewer;
		this.comment = comment;
		this.reviewedAt = Instant.now();
	}

	public synchronized void cancel() {
		this.status = HumanApprovalStatus.CANCELLED;
		this.reviewedAt = Instant.now();
	}

	public String getApprovalId() {
		return approvalId;
	}

	public String getTaskId() {
		return taskId;
	}

	public String getSessionId() {
		return sessionId;
	}

	public String getTeamId() {
		return teamId;
	}

	public String getNodeId() {
		return nodeId;
	}

	public HumanApprovalStatus getStatus() {
		return status;
	}

	public String getRequester() {
		return requester;
	}

	public String getReviewer() {
		return reviewer;
	}

	public String getComment() {
		return comment;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getReviewedAt() {
		return reviewedAt;
	}
}
