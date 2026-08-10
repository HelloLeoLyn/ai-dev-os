package com.aidevos.orchestrator.feedback;

import java.time.Instant;

/**
 * One pull request feedback cycle: a failed CI run is linked to its repair,
 * the repaired ChangeSet waits for human review, then the approved change is
 * committed, pushed and re-checked by CI. All refs (repair, change, commit,
 * ci run) are tracked so the whole loop is inspectable from one record.
 */
public class PrFeedbackRecord {

	private final String feedbackId;
	private final String taskId;
	private final String pullRequestId;
	private final Instant createdAt;
	private volatile String repairTaskId;
	private volatile String changeId;
	private volatile String commitId;
	private volatile String ciRunId;
	private volatile FeedbackStatus status = FeedbackStatus.CREATED;
	private volatile int retryCount = 0;
	private volatile Instant updatedAt;

	public PrFeedbackRecord(String feedbackId, String taskId, String pullRequestId,
			String repairTaskId, String changeId, String commitId, String ciRunId,
			FeedbackStatus status, int retryCount, Instant createdAt) {
		this.feedbackId = feedbackId;
		this.taskId = taskId;
		this.pullRequestId = pullRequestId == null ? "" : pullRequestId;
		this.repairTaskId = repairTaskId == null ? "" : repairTaskId;
		this.changeId = changeId == null ? "" : changeId;
		this.commitId = commitId == null ? "" : commitId;
		this.ciRunId = ciRunId == null ? "" : ciRunId;
		this.status = status == null ? FeedbackStatus.CREATED : status;
		this.retryCount = retryCount;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
	}

	public synchronized void linkRepair(String repairTaskId) {
		this.repairTaskId = repairTaskId == null ? "" : repairTaskId;
		this.updatedAt = Instant.now();
	}

	public synchronized void linkChange(String changeId) {
		this.changeId = changeId == null ? "" : changeId;
		this.updatedAt = Instant.now();
	}

	public synchronized void linkCommit(String commitId) {
		this.commitId = commitId == null ? "" : commitId;
		this.updatedAt = Instant.now();
	}

	public synchronized void linkCiRun(String ciRunId) {
		this.ciRunId = ciRunId == null ? "" : ciRunId;
		this.updatedAt = Instant.now();
	}

	public synchronized void markRepairing() {
		requireNotTerminal();
		this.status = FeedbackStatus.REPAIRING;
		this.updatedAt = Instant.now();
	}

	public synchronized void markWaitingReview() {
		requireStatus(FeedbackStatus.REPAIRING, "Only a REPAIRING feedback can wait for review");
		this.status = FeedbackStatus.WAITING_REVIEW;
		this.updatedAt = Instant.now();
	}

	public synchronized void markPushed() {
		requireStatus(FeedbackStatus.WAITING_REVIEW, "Only a WAITING_REVIEW feedback can be pushed");
		this.status = FeedbackStatus.PUSHED;
		this.updatedAt = Instant.now();
	}

	public synchronized void markRechecking() {
		requireStatus(FeedbackStatus.PUSHED, "Only a PUSHED feedback can recheck");
		this.status = FeedbackStatus.RECHECKING;
		this.updatedAt = Instant.now();
	}

	public synchronized void markSuccess() {
		requireStatus(FeedbackStatus.RECHECKING, "Only a RECHECKING feedback can succeed");
		this.status = FeedbackStatus.SUCCESS;
		this.updatedAt = Instant.now();
	}

	public synchronized void markFailed() {
		if (this.status == FeedbackStatus.SUCCESS) {
			throw new IllegalStateException("A SUCCESS feedback cannot fail");
		}
		this.status = FeedbackStatus.FAILED;
		this.updatedAt = Instant.now();
	}

	public synchronized void incrementRetry() {
		this.retryCount++;
		this.updatedAt = Instant.now();
	}

	private void requireStatus(FeedbackStatus expected, String message) {
		if (this.status != expected) {
			throw new IllegalStateException(message + " (current: " + this.status + ")");
		}
	}

	private void requireNotTerminal() {
		if (this.status == FeedbackStatus.SUCCESS) {
			throw new IllegalStateException("A SUCCESS feedback cannot change state");
		}
	}

	public String getFeedbackId() {
		return feedbackId;
	}

	public String getTaskId() {
		return taskId;
	}

	public String getPullRequestId() {
		return pullRequestId;
	}

	public String getRepairTaskId() {
		return repairTaskId;
	}

	public String getChangeId() {
		return changeId;
	}

	public String getCommitId() {
		return commitId;
	}

	public String getCiRunId() {
		return ciRunId;
	}

	public FeedbackStatus getStatus() {
		return status;
	}

	public int getRetryCount() {
		return retryCount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
