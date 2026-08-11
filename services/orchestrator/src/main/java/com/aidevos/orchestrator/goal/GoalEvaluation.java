package com.aidevos.orchestrator.goal;

/**
 * Evaluation of a goal's completion derived from the orchestration task
 * outcomes, the runtime sessions and the optimization recommendations.
 */
public record GoalEvaluation(String goalId, int completedTasks, int totalTasks,
		double progress, int remainingWork, double confidence) {
}
