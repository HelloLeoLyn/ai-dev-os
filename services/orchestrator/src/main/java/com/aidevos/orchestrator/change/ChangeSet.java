package com.aidevos.orchestrator.change;

import java.time.Instant;

/**
 * A snapshot of one AI code modification: the git diff and change statistics
 * captured at creation time, linked to the task, workspace and execution that
 * produced it, plus the review state (CREATED -> REVIEWING -> APPROVED |
 * REJECTED). COMMITTED is reserved for a later phase; this class never runs
 * git commit itself.
 */
public class ChangeSet {

	private final String changeId;
	private final String taskId;
	private final String workspaceId;
	private final String projectId;
	private final String executionId;
	private final String branch;
	private final String diff;
	private final String diffStat;
	private final int filesChanged;
	private final int insertions;
	private final int deletions;
	private final int modified;
	private final int added;
	private final int deleted;
	private final Instant createdAt;
	private volatile ChangeStatus status = ChangeStatus.CREATED;
	private volatile Instant updatedAt;
	private volatile Instant reviewedAt;
	private volatile String reviewedBy;

	public ChangeSet(String changeId, String taskId, String workspaceId, String projectId,
			String executionId, String branch, String diff, String diffStat, int filesChanged,
			int insertions, int deletions, int modified, int added, int deleted,
			Instant createdAt) {
		this.changeId = changeId;
		this.taskId = taskId;
		this.workspaceId = workspaceId;
		this.projectId = projectId;
		this.executionId = executionId;
		this.branch = branch == null ? "" : branch;
		this.diff = diff == null ? "" : diff;
		this.diffStat = diffStat == null ? "" : diffStat;
		this.filesChanged = filesChanged;
		this.insertions = insertions;
		this.deletions = deletions;
		this.modified = modified;
		this.added = added;
		this.deleted = deleted;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
	}

	public synchronized void markReviewing() {
		requireStatus(ChangeStatus.CREATED, "Only a CREATED change can start review");
		this.status = ChangeStatus.REVIEWING;
		this.updatedAt = Instant.now();
	}

	public synchronized void markApproved(String reviewer) {
		requireStatus(ChangeStatus.REVIEWING, "Only a REVIEWING change can be approved");
		this.status = ChangeStatus.APPROVED;
		this.reviewedBy = reviewer;
		this.reviewedAt = Instant.now();
		this.updatedAt = this.reviewedAt;
	}

	public synchronized void markRejected(String reviewer) {
		requireStatus(ChangeStatus.REVIEWING, "Only a REVIEWING change can be rejected");
		this.status = ChangeStatus.REJECTED;
		this.reviewedBy = reviewer;
		this.reviewedAt = Instant.now();
		this.updatedAt = this.reviewedAt;
	}

	private void requireStatus(ChangeStatus expected, String message) {
		if (this.status != expected) {
			throw new IllegalStateException(message + " (current: " + this.status + ")");
		}
	}

	public String getChangeId() {
		return changeId;
	}

	public String getTaskId() {
		return taskId;
	}

	public String getWorkspaceId() {
		return workspaceId;
	}

	public String getProjectId() {
		return projectId;
	}

	public String getExecutionId() {
		return executionId;
	}

	public String getBranch() {
		return branch;
	}

	public String getDiff() {
		return diff;
	}

	public String getDiffStat() {
		return diffStat;
	}

	public int getFilesChanged() {
		return filesChanged;
	}

	public int getInsertions() {
		return insertions;
	}

	public int getDeletions() {
		return deletions;
	}

	public int getModified() {
		return modified;
	}

	public int getAdded() {
		return added;
	}

	public int getDeleted() {
		return deleted;
	}

	public ChangeStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getReviewedAt() {
		return reviewedAt;
	}

	public String getReviewedBy() {
		return reviewedBy;
	}
}
