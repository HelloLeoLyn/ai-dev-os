package com.aidevos.orchestrator.remote;

import java.time.Instant;

/**
 * One push of a committed branch to a git remote: the remote name, its URL,
 * the pushed branch and the push lifecycle status. Pushing never merges,
 * rebases, deletes branches or creates pull requests.
 */
public class RemoteBranchRecord {

	private final String remoteId;
	private final String taskId;
	private final String workspaceId;
	private final String commitId;
	private final String branch;
	private final String remote;
	private final String url;
	private final Instant createdAt;
	private volatile RemoteStatus status = RemoteStatus.PENDING;
	private volatile Instant updatedAt;

	public RemoteBranchRecord(String remoteId, String taskId, String workspaceId,
			String commitId, String branch, String remote, String url, Instant createdAt) {
		this.remoteId = remoteId;
		this.taskId = taskId;
		this.workspaceId = workspaceId;
		this.commitId = commitId;
		this.branch = branch == null ? "" : branch;
		this.remote = remote;
		this.url = url == null ? "" : url;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
	}

	public synchronized void markPushing() {
		requireStatus(RemoteStatus.PENDING, "Only a PENDING push can start");
		this.status = RemoteStatus.PUSHING;
		this.updatedAt = Instant.now();
	}

	public synchronized void markSuccess() {
		requireStatus(RemoteStatus.PUSHING, "Only a PUSHING push can succeed");
		this.status = RemoteStatus.SUCCESS;
		this.updatedAt = Instant.now();
	}

	public synchronized void markFailed() {
		if (this.status != RemoteStatus.PUSHING && this.status != RemoteStatus.PENDING) {
			throw new IllegalStateException("Cannot fail a " + this.status + " push");
		}
		this.status = RemoteStatus.FAILED;
		this.updatedAt = Instant.now();
	}

	private void requireStatus(RemoteStatus expected, String message) {
		if (this.status != expected) {
			throw new IllegalStateException(message + " (current: " + this.status + ")");
		}
	}

	public String getRemoteId() {
		return remoteId;
	}

	public String getTaskId() {
		return taskId;
	}

	public String getWorkspaceId() {
		return workspaceId;
	}

	public String getCommitId() {
		return commitId;
	}

	public String getBranch() {
		return branch;
	}

	public String getRemote() {
		return remote;
	}

	public String getUrl() {
		return url;
	}

	public RemoteStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
