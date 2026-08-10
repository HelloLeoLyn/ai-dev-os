package com.aidevos.orchestrator.commit;

import java.time.Instant;

/**
 * One commit created from an approved change set: the resulting git hash, the
 * commit message and the commit lifecycle status. Committing never pushes,
 * merges or touches remotes.
 */
public class CommitRecord {

	private final String commitId;
	private final String changeId;
	private final String taskId;
	private final String workspaceId;
	private final String branch;
	private final String message;
	private final Instant createdAt;
	private volatile CommitStatus status = CommitStatus.PENDING;
	private volatile String gitHash;
	private volatile Instant updatedAt;

	public CommitRecord(String commitId, String changeId, String taskId, String workspaceId,
			String branch, String message, Instant createdAt) {
		this.commitId = commitId;
		this.changeId = changeId;
		this.taskId = taskId;
		this.workspaceId = workspaceId;
		this.branch = branch == null ? "" : branch;
		this.message = message;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
	}

	public synchronized void markCommitting() {
		requireStatus(CommitStatus.PENDING, "Only a PENDING commit can start");
		this.status = CommitStatus.COMMITTING;
		this.updatedAt = Instant.now();
	}

	public synchronized void markSuccess(String hash) {
		requireStatus(CommitStatus.COMMITTING, "Only a COMMITTING commit can succeed");
		this.status = CommitStatus.SUCCESS;
		this.gitHash = hash;
		this.updatedAt = Instant.now();
	}

	public synchronized void markFailed() {
		if (this.status != CommitStatus.COMMITTING && this.status != CommitStatus.PENDING) {
			throw new IllegalStateException("Cannot fail a " + this.status + " commit");
		}
		this.status = CommitStatus.FAILED;
		this.updatedAt = Instant.now();
	}

	private void requireStatus(CommitStatus expected, String message) {
		if (this.status != expected) {
			throw new IllegalStateException(message + " (current: " + this.status + ")");
		}
	}

	private CommitRecord(String commitId, String changeId, String taskId, String workspaceId,
			String branch, String message, CommitStatus status, String gitHash,
			Instant createdAt, Instant updatedAt) {
		this.commitId = commitId;
		this.changeId = changeId;
		this.taskId = taskId;
		this.workspaceId = workspaceId;
		this.branch = branch == null ? "" : branch;
		this.message = message;
		this.createdAt = createdAt;
		this.status = status == null ? CommitStatus.PENDING : status;
		this.gitHash = gitHash;
		this.updatedAt = updatedAt == null ? createdAt : updatedAt;
	}

	/**
	 * Reconstructs a persisted commit record without running state transitions.
	 * Used by the PostgreSQL repository.
	 */
	public static CommitRecord restore(String commitId, String changeId, String taskId,
			String workspaceId, String branch, String message, CommitStatus status,
			String gitHash, Instant createdAt, Instant updatedAt) {
		return new CommitRecord(commitId, changeId, taskId, workspaceId, branch, message,
			status, gitHash, createdAt, updatedAt);
	}

	public String getCommitId() {
		return commitId;
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

	public String getBranch() {
		return branch;
	}

	public String getMessage() {
		return message;
	}

	public CommitStatus getStatus() {
		return status;
	}

	public String getGitHash() {
		return gitHash;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
