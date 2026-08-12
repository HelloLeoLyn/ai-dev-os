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
	private final String workspaceId;
	private final ExecutionMode executionMode;
	private final Instant createdAt;
	private volatile TaskStatus status = TaskStatus.CREATED;
	private volatile Instant updatedAt;
	private volatile String approvalId;
	private volatile String planRunId;
	private volatile String errorMessage;

	public TaskRecord(String taskId, String name, String description) {
		this(taskId, name, description, null, null, ExecutionMode.READ_WRITE);
	}

	public TaskRecord(String taskId, String name, String description, String projectId) {
		this(taskId, name, description, projectId, null, ExecutionMode.READ_WRITE);
	}

	public TaskRecord(String taskId, String name, String description, String projectId,
			String workspaceId) {
		this(taskId, name, description, projectId, workspaceId, ExecutionMode.READ_WRITE);
	}

	public TaskRecord(String taskId, String name, String description, String projectId,
			String workspaceId, ExecutionMode executionMode) {
		this.taskId = taskId;
		this.name = name;
		this.description = description;
		this.projectId = projectId == null || projectId.isBlank() ? "default" : projectId.trim();
		this.workspaceId = workspaceId == null || workspaceId.isBlank()
			? null : workspaceId.trim();
		this.executionMode = executionMode == null ? ExecutionMode.READ_WRITE : executionMode;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	private TaskRecord(String taskId, String name, String description, String projectId,
			String workspaceId, ExecutionMode executionMode, TaskStatus status, Instant createdAt, Instant updatedAt,
			String approvalId, String planRunId, String errorMessage) {
		this.taskId = taskId;
		this.name = name;
		this.description = description;
		this.projectId = projectId == null || projectId.isBlank() ? "default"
			: projectId.trim();
		this.workspaceId = workspaceId == null || workspaceId.isBlank()
			? null : workspaceId.trim();
		this.executionMode = executionMode == null ? ExecutionMode.READ_WRITE : executionMode;
		this.status = status == null ? TaskStatus.CREATED : status;
		this.createdAt = createdAt == null ? Instant.now() : createdAt;
		this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
		this.approvalId = approvalId;
		this.planRunId = planRunId;
		this.errorMessage = errorMessage;
	}

	/**
	 * Reconstructs a persisted task without running state transitions. Used by
	 * the PostgreSQL repository; the normal lifecycle methods remain the only
	 * way to change state at runtime.
	 */
	public static TaskRecord restore(String taskId, String name, String description,
			String projectId, String workspaceId, TaskStatus status, Instant createdAt,
			Instant updatedAt, String approvalId, String planRunId, String errorMessage) {
		return restore(taskId, name, description, projectId, workspaceId,
			ExecutionMode.READ_WRITE, status, createdAt, updatedAt, approvalId, planRunId,
			errorMessage);
	}

	public static TaskRecord restore(String taskId, String name, String description,
			String projectId, String workspaceId, ExecutionMode executionMode,
			TaskStatus status, Instant createdAt, Instant updatedAt, String approvalId,
			String planRunId, String errorMessage) {
		return new TaskRecord(taskId, name, description, projectId, workspaceId,
			executionMode, status,
			createdAt, updatedAt, approvalId, planRunId, errorMessage);
	}

	public synchronized void markPlanning(String approvalId) {
		this.approvalId = approvalId;
		this.status = TaskStatus.PLANNING;
		this.updatedAt = Instant.now();
	}

	public synchronized void markApproved() {
		if (isTerminal()) {
			return;
		}
		this.status = TaskStatus.APPROVED;
		this.updatedAt = Instant.now();
	}

	public synchronized void markRunning() {
		if (isTerminal()) {
			return;
		}
		this.status = TaskStatus.RUNNING;
		this.updatedAt = Instant.now();
	}

	public synchronized void markRejected(String reason) {
		this.status = TaskStatus.REJECTED;
		this.errorMessage = reason;
		this.updatedAt = Instant.now();
	}

	public synchronized void markCoding() {
		if (isTerminal()) {
			return;
		}
		this.status = TaskStatus.CODING;
		this.updatedAt = Instant.now();
	}

	public synchronized void markTesting() {
		if (isTerminal()) {
			return;
		}
		this.status = TaskStatus.TESTING;
		this.updatedAt = Instant.now();
	}

	public synchronized void markCompleted() {
		this.status = TaskStatus.COMPLETED;
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

	private boolean isTerminal() {
		return status == TaskStatus.SUCCESS || status == TaskStatus.FAILED
			|| status == TaskStatus.COMPLETED || status == TaskStatus.REJECTED;
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

	public String getWorkspaceId() {
		return workspaceId;
	}

	public ExecutionMode getExecutionMode() {
		return executionMode;
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
