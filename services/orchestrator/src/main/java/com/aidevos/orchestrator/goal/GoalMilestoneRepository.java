package com.aidevos.orchestrator.goal;

import java.util.List;

/**
 * Persistence contract for goal milestones.
 */
public interface GoalMilestoneRepository {

	void save(GoalMilestone milestone);

	GoalMilestone get(String milestoneId);

	List<GoalMilestone> listByGoal(String goalId);
}
