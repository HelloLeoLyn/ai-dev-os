package com.aidevos.orchestrator.goal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

/**
 * In-memory goal store, matching the in-memory mode of the runtime,
 * orchestrator and planner stores.
 */
@Repository
public class InMemoryGoalRepository implements GoalRepository {

	private final Map<String, Goal> goals = new LinkedHashMap<>();

	@Override
	public synchronized void save(Goal goal) {
		if (goal != null) {
			goals.put(goal.getGoalId(), goal);
		}
	}

	@Override
	public synchronized Goal get(String goalId) {
		return goalId == null ? null : goals.get(goalId);
	}

	@Override
	public synchronized List<Goal> list() {
		List<Goal> result = new ArrayList<>(goals.values());
		result.sort(Comparator.comparing(Goal::getCreatedAt).reversed());
		return result;
	}

	@Override
	public synchronized List<Goal> listByProject(String projectId) {
		return list().stream()
			.filter(goal -> projectId == null || projectId.equals(goal.getProjectId()))
			.toList();
	}
}
