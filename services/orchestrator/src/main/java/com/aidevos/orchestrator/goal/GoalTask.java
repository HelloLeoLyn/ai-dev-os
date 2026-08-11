package com.aidevos.orchestrator.goal;

import java.time.Instant;

/**
 * Link between a goal and one task generated into the orchestrator task pool.
 * relationType describes the relationship (SUB_TASK / PREREQUISITE /
 * MILESTONE).
 */
public class GoalTask {

	private final String goalId;
	private final String taskId;
	private final String relationType;
	private final Instant createdAt;

	public GoalTask(String goalId, String taskId, String relationType) {
		this.goalId = goalId;
		this.taskId = taskId;
		this.relationType = relationType == null || relationType.isBlank()
			? "SUB_TASK" : relationType;
		this.createdAt = Instant.now();
	}

	public String getGoalId() {
		return goalId;
	}

	public String getTaskId() {
		return taskId;
	}

	public String getRelationType() {
		return relationType;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
