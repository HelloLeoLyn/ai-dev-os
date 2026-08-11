package com.aidevos.orchestrator.goal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

/**
 * In-memory goal -> task link store.
 */
@Repository
public class InMemoryGoalTaskRepository implements GoalTaskRepository {

	private final Map<String, GoalTask> tasks = new LinkedHashMap<>();

	@Override
	public synchronized void save(GoalTask task) {
		if (task != null) {
			tasks.put(task.getTaskId(), task);
		}
	}

	@Override
	public synchronized GoalTask findByTaskId(String taskId) {
		return taskId == null ? null : tasks.get(taskId);
	}

	@Override
	public synchronized List<GoalTask> listByGoal(String goalId) {
		List<GoalTask> result = new ArrayList<>();
		for (GoalTask task : tasks.values()) {
			if (goalId.equals(task.getGoalId())) {
				result.add(task);
			}
		}
		result.sort(Comparator.comparing(GoalTask::getCreatedAt));
		return result;
	}
}
