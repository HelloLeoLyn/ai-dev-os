package com.aidevos.orchestrator.workspace;

import java.time.Instant;

/**
 * A git workspace bound to a project: an existing local directory that agents
 * operate on. Phase 1 only manages existing local directories; clone, pull,
 * checkout and automatic branch creation are intentionally not supported.
 */
public class Workspace {

	private final String workspaceId;
	private final String projectId;
	private final String path;
	private final String branch;
	private final Instant createdAt;
	private volatile WorkspaceStatus status;
	private volatile Instant updatedAt;

	public Workspace(String workspaceId, String projectId, String path, String branch,
			WorkspaceStatus status, Instant createdAt, Instant updatedAt) {
		this.workspaceId = workspaceId;
		this.projectId = projectId;
		this.path = path;
		this.branch = branch;
		this.status = status == null ? WorkspaceStatus.READY : status;
		this.createdAt = createdAt == null ? Instant.now() : createdAt;
		this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
	}

	public synchronized void markReady() {
		this.status = WorkspaceStatus.READY;
		this.updatedAt = Instant.now();
	}

	public synchronized void lock() {
		if (this.status == WorkspaceStatus.FAILED) {
			return;
		}
		this.status = WorkspaceStatus.LOCKED;
		this.updatedAt = Instant.now();
	}

	public synchronized void markCleanup() {
		this.status = WorkspaceStatus.CLEANUP;
		this.updatedAt = Instant.now();
	}

	public synchronized void markFailed() {
		this.status = WorkspaceStatus.FAILED;
		this.updatedAt = Instant.now();
	}

	public String getWorkspaceId() {
		return workspaceId;
	}

	public String getProjectId() {
		return projectId;
	}

	public String getPath() {
		return path;
	}

	public String getBranch() {
		return branch;
	}

	public WorkspaceStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
