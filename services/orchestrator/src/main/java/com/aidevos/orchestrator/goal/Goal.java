package com.aidevos.orchestrator.goal;

import java.time.Instant;

/**
 * One autonomous goal: a long-lived objective that is decomposed into
 * milestones and generated tasks instead of being executed as a single task.
 * Progress is recomputed from the orchestration task outcomes.
 */
public class Goal {

	private final String goalId;
	private final String projectId;
	private final String title;
	private final String description;
	private final GoalPriority priority;
	private volatile GoalStatus status;
	private volatile double progress;
	private final Instant createdAt;
	private volatile Instant updatedAt;

	public Goal(String goalId, String projectId, String title, String description,
			GoalPriority priority) {
		this.goalId = goalId;
		this.projectId = projectId == null || projectId.isBlank() ? "default"
			: projectId.trim();
		this.title = title;
		this.description = description;
		this.priority = priority == null ? GoalPriority.NORMAL : priority;
		this.status = GoalStatus.CREATED;
		this.progress = 0.0;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	public synchronized void markPlanning() {
		this.status = GoalStatus.PLANNING;
		this.updatedAt = Instant.now();
	}

	public synchronized void markRunning() {
		this.status = GoalStatus.RUNNING;
		this.updatedAt = Instant.now();
	}

	public synchronized void markPaused() {
		this.status = GoalStatus.PAUSED;
		this.updatedAt = Instant.now();
	}

	public synchronized void markCompleted() {
		this.status = GoalStatus.COMPLETED;
		this.progress = 100.0;
		this.updatedAt = Instant.now();
	}

	public synchronized void markFailed() {
		this.status = GoalStatus.FAILED;
		this.updatedAt = Instant.now();
	}

	public synchronized void setProgress(double progress) {
		this.progress = Math.max(0.0, Math.min(100.0, progress));
		this.updatedAt = Instant.now();
	}

	public String getGoalId() {
		return goalId;
	}

	public String getProjectId() {
		return projectId;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public GoalPriority getPriority() {
		return priority;
	}

	public GoalStatus getStatus() {
		return status;
	}

	public double getProgress() {
		return progress;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
