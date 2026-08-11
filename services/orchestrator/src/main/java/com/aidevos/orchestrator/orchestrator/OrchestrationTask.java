package com.aidevos.orchestrator.orchestrator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One task managed by the autonomous orchestrator: its priority, queue
 * status, the agents it requires and the agents the orchestrator assigned
 * automatically. Timestamps track when the task was created and started.
 */
public class OrchestrationTask {

	private final String taskId;
	private final String taskType;
	private final TaskPriority priority;
	private final List<String> requiredAgents;
	private volatile OrchestrationTaskStatus status;
	private final Instant createdAt;
	private volatile Instant startedAt;
	private volatile Instant updatedAt;
	private volatile Instant completedAt;
	private volatile List<String> assignedAgents = List.of();
	private volatile String errorMessage;

	public OrchestrationTask(String taskId, String taskType, TaskPriority priority,
			List<String> requiredAgents) {
		this.taskId = taskId;
		this.taskType = taskType == null || taskType.isBlank() ? "GENERAL" : taskType.trim();
		this.priority = priority == null ? TaskPriority.NORMAL : priority;
		this.requiredAgents = requiredAgents == null ? List.of() : List.copyOf(requiredAgents);
		this.status = OrchestrationTaskStatus.QUEUED;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	public synchronized void markRunning() {
		this.status = OrchestrationTaskStatus.RUNNING;
		this.startedAt = Instant.now();
		this.updatedAt = Instant.now();
	}

	public synchronized void markPaused() {
		this.status = OrchestrationTaskStatus.PAUSED;
		this.updatedAt = Instant.now();
	}

	public synchronized void markCompleted() {
		this.status = OrchestrationTaskStatus.COMPLETED;
		this.completedAt = Instant.now();
		this.updatedAt = Instant.now();
	}

	public synchronized void markFailed(String error) {
		this.status = OrchestrationTaskStatus.FAILED;
		this.errorMessage = error;
		this.completedAt = Instant.now();
		this.updatedAt = Instant.now();
	}

	public synchronized void setAssignedAgents(List<String> assignedAgents) {
		this.assignedAgents = assignedAgents == null ? List.of()
			: List.copyOf(assignedAgents);
		this.updatedAt = Instant.now();
	}

	public String getTaskId() {
		return taskId;
	}

	public String getTaskType() {
		return taskType;
	}

	public TaskPriority getPriority() {
		return priority;
	}

	public List<String> getRequiredAgents() {
		return List.copyOf(requiredAgents);
	}

	public OrchestrationTaskStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public List<String> getAssignedAgents() {
		return List.copyOf(assignedAgents);
	}

	public String getErrorMessage() {
		return errorMessage;
	}
}
