package com.aidevos.orchestrator.goal;

import java.util.List;

/**
 * Persistence contract for goal -> task links.
 */
public interface GoalTaskRepository {

	void save(GoalTask task);

	GoalTask findByTaskId(String taskId);

	List<GoalTask> listByGoal(String goalId);
}
