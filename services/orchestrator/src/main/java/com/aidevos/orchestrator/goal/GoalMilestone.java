package com.aidevos.orchestrator.goal;

import java.time.Instant;

/**
 * One milestone of a goal (planning / implementation / verification). Its
 * progress is derived from the tasks assigned to it.
 */
public class GoalMilestone {

	private final String milestoneId;
	private final String goalId;
	private final String title;
	private final String description;
	private volatile MilestoneStatus status;
	private volatile double progress;
	private final Instant createdAt;

	public GoalMilestone(String milestoneId, String goalId, String title, String description) {
		this.milestoneId = milestoneId;
		this.goalId = goalId;
		this.title = title;
		this.description = description;
		this.status = MilestoneStatus.CREATED;
		this.progress = 0.0;
		this.createdAt = Instant.now();
	}

	public synchronized void markRunning() {
		this.status = MilestoneStatus.RUNNING;
	}

	public synchronized void markCompleted() {
		this.status = MilestoneStatus.COMPLETED;
		this.progress = 100.0;
	}

	public synchronized void markFailed() {
		this.status = MilestoneStatus.FAILED;
	}

	public synchronized void setProgress(double progress) {
		this.progress = Math.max(0.0, Math.min(100.0, progress));
		if (this.progress >= 100.0 && this.status != MilestoneStatus.FAILED) {
			this.status = MilestoneStatus.COMPLETED;
		}
		else if (this.progress > 0.0 && this.status == MilestoneStatus.CREATED) {
			this.status = MilestoneStatus.RUNNING;
		}
	}

	public String getMilestoneId() {
		return milestoneId;
	}

	public String getGoalId() {
		return goalId;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public MilestoneStatus getStatus() {
		return status;
	}

	public double getProgress() {
		return progress;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
