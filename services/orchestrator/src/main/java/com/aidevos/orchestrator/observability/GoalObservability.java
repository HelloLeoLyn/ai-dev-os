package com.aidevos.orchestrator.observability;

import java.util.List;

import com.aidevos.orchestrator.goal.GoalEvaluation;
import com.aidevos.orchestrator.goal.GoalMilestone;
import com.aidevos.orchestrator.goal.GoalTask;

/**
 * Goal-level observability bundle: the goal status and progress plus its
 * milestones, generated tasks and the completion evaluation. The timeline of
 * a goal is Goal -> Milestone -> Task -> Runtime -> Result -> Progress; the
 * per-task runtime details stay in the TaskObservability of each generated
 * task.
 */
public record GoalObservability(String goalId, String status, double progress,
		List<GoalMilestone> milestones, List<GoalTask> tasks, GoalEvaluation evaluation) {

	public GoalObservability {
		milestones = milestones == null ? List.of() : List.copyOf(milestones);
		tasks = tasks == null ? List.of() : List.copyOf(tasks);
	}
}
