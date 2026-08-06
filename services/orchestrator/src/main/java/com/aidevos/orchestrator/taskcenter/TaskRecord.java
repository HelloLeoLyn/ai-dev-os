package com.aidevos.orchestrator.taskcenter;

import java.time.Instant;

/**
 * Task Center task tracking the User Request lifecycle. Execution itself is
 * delegated to the existing planner, approval and plan-run flow.
 */
public class TaskRecord {

	private final String taskId;
	private final String name;
	private final String description;
	private final String projectId;
	private final Instant createdAt;
	private volatile TaskStatus status = TaskStatus.CREATED;
	private volatile Instant updatedAt;
	private volatile String approvalId;
	private volatile String planRunId;
	private volatile String errorMessage;

	public TaskRecord(String taskId, String name, String description) {
		this(taskId, name, description, null);
	}

	public TaskRecord(String taskId, String name, String description, String projectId) {
		this.taskId = taskId;
		this.name = name;
		this.description = description;
		this.projectId = projectId == null || projectId.isBlank() ? "default" : projectId.trim();
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	public synchronized void markPlanning(String approvalId) {
		this.approvalId = approvalId;
		this.status = TaskStatus.PLANNING;
		this.updatedAt = Instant.now();
	}

	public synchronized void markApproved() {
		if (status == TaskStatus.SUCCESS || status == TaskStatus.FAILED) {
			return;
		}
		this.status = TaskStatus.APPROVED;
		this.updatedAt = Instant.now();
	}

	public synchronized void markRunning() {
		if (status == TaskStatus.SUCCESS || status == TaskStatus.FAILED) {
			return;
		}
		this.status = TaskStatus.RUNNING;
		this.updatedAt = Instant.now();
	}

	public synchronized void markSuccess() {
		this.status = TaskStatus.SUCCESS;
		this.updatedAt = Instant.now();
	}

	public synchronized void markFailed(String error) {
		this.status = TaskStatus.FAILED;
		this.errorMessage = error;
		this.updatedAt = Instant.now();
	}

	public synchronized void setPlanRunId(String planRunId) {
		this.planRunId = planRunId;
		this.updatedAt = Instant.now();
	}

	public String getTaskId() {
		return taskId;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getProjectId() {
		return projectId;
	}

	public TaskStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public String getApprovalId() {
		return approvalId;
	}

	public String getPlanRunId() {
		return planRunId;
	}

	public String getErrorMessage() {
		return errorMessage;
	}
}
