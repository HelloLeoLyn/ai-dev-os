package com.aidevos.orchestrator.pr;

import java.time.Instant;

/**
 * One pull request opened for a pushed commit: the branch, target branch,
 * title, description and provider URL plus the PR lifecycle status. This
 * phase only tracks state and never merges, pulls or modifies code.
 */
public class PullRequestRecord {

	private final String pullRequestId;
	private final String taskId;
	private final String commitId;
	private final String remoteId;
	private final String branch;
	private final String targetBranch;
	private final String title;
	private final String description;
	private final Instant createdAt;
	private volatile PullRequestStatus status = PullRequestStatus.CREATED;
	private volatile String url;
	private volatile String externalId;
	private volatile Instant updatedAt;

	public PullRequestRecord(String pullRequestId, String taskId, String commitId,
			String remoteId, String branch, String targetBranch, String title,
			String description, String url, Instant createdAt) {
		this.pullRequestId = pullRequestId;
		this.taskId = taskId;
		this.commitId = commitId;
		this.remoteId = remoteId;
		this.branch = branch == null ? "" : branch;
		this.targetBranch = targetBranch == null ? "" : targetBranch;
		this.title = title == null ? "" : title;
		this.description = description == null ? "" : description;
		this.url = url;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
	}

	public synchronized void markOpened() {
		requireStatus(PullRequestStatus.CREATED, "Only a CREATED pull request can open");
		this.status = PullRequestStatus.OPEN;
		this.updatedAt = Instant.now();
	}

	public synchronized void markMerged() {
		requireStatus(PullRequestStatus.OPEN, "Only an OPEN pull request can merge");
		this.status = PullRequestStatus.MERGED;
		this.updatedAt = Instant.now();
	}

	public synchronized void markClosed() {
		requireStatus(PullRequestStatus.OPEN, "Only an OPEN pull request can close");
		this.status = PullRequestStatus.CLOSED;
		this.updatedAt = Instant.now();
	}

	public synchronized void markFailed() {
		if (this.status != PullRequestStatus.CREATED && this.status != PullRequestStatus.OPEN) {
			throw new IllegalStateException("Cannot fail a " + this.status + " pull request");
		}
		this.status = PullRequestStatus.FAILED;
		this.updatedAt = Instant.now();
	}

	public synchronized void updateUrl(String url) {
		this.url = url;
		this.updatedAt = Instant.now();
	}

	public synchronized void updateExternalId(String externalId) {
		this.externalId = externalId;
		this.updatedAt = Instant.now();
	}

	private void requireStatus(PullRequestStatus expected, String message) {
		if (this.status != expected) {
			throw new IllegalStateException(message + " (current: " + this.status + ")");
		}
	}

	public String getPullRequestId() {
		return pullRequestId;
	}

	public String getTaskId() {
		return taskId;
	}

	public String getCommitId() {
		return commitId;
	}

	public String getRemoteId() {
		return remoteId;
	}

	public String getBranch() {
		return branch;
	}

	public String getTargetBranch() {
		return targetBranch;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getUrl() {
		return url;
	}

	public String getExternalId() {
		return externalId;
	}

	public PullRequestStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
