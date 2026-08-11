package com.aidevos.orchestrator.goal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

/**
 * In-memory goal milestone store.
 */
@Repository
public class InMemoryGoalMilestoneRepository implements GoalMilestoneRepository {

	private final Map<String, GoalMilestone> milestones = new LinkedHashMap<>();

	@Override
	public synchronized void save(GoalMilestone milestone) {
		if (milestone != null) {
			milestones.put(milestone.getMilestoneId(), milestone);
		}
	}

	@Override
	public synchronized GoalMilestone get(String milestoneId) {
		return milestoneId == null ? null : milestones.get(milestoneId);
	}

	@Override
	public synchronized List<GoalMilestone> listByGoal(String goalId) {
		List<GoalMilestone> result = new ArrayList<>();
		for (GoalMilestone milestone : milestones.values()) {
			if (goalId.equals(milestone.getGoalId())) {
				result.add(milestone);
			}
		}
		result.sort(Comparator.comparing(GoalMilestone::getCreatedAt));
		return result;
	}
}
