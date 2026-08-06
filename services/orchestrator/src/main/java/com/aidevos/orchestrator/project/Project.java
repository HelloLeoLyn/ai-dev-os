package com.aidevos.orchestrator.project;

import java.time.Instant;

/**
 * A managed development project: the isolation boundary for tasks, memory and
 * agent execution.
 */
public class Project {

	private final String projectId;
	private final String name;
	private final String path;
	private final String description;
	private final Instant createdAt;
	private volatile ProjectStatus status;
	private volatile Instant updatedAt;

	public Project(String projectId, String name, String path, String description,
			ProjectStatus status, Instant createdAt, Instant updatedAt) {
		this.projectId = projectId;
		this.name = name;
		this.path = path;
		this.description = description;
		this.status = status == null ? ProjectStatus.ACTIVE : status;
		this.createdAt = createdAt == null ? Instant.now() : createdAt;
		this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
	}

	public synchronized void markActive() {
		this.status = ProjectStatus.ACTIVE;
		this.updatedAt = Instant.now();
	}

	public synchronized void markDisabled() {
		this.status = ProjectStatus.DISABLED;
		this.updatedAt = Instant.now();
	}

	public synchronized void markArchived() {
		this.status = ProjectStatus.ARCHIVED;
		this.updatedAt = Instant.now();
	}

	public String getProjectId() {
		return projectId;
	}

	public String getName() {
		return name;
	}

	public String getPath() {
		return path;
	}

	public String getDescription() {
		return description;
	}

	public ProjectStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
