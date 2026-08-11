package com.aidevos.orchestrator.planner;

import java.time.Instant;
import java.util.List;

/**
 * An execution plan produced by the dynamic planning engine: the goal of the
 * task, the ordered steps (each with an agent, tools and dependencies), the
 * selected agents, an estimated cost, a risk level and an evaluation score.
 * The plan is only a suggestion layer: the orchestrator converts it to an
 * execution graph through {@code generateGraph} and runs it through the
 * existing runtime; the graph itself is never modified by the planner.
 */
public record Plan(String planId, String taskId, String goal, List<PlanStep> steps,
		List<String> selectedAgents, double estimatedCost, RiskLevel riskLevel, double score,
		Instant createdAt) {

	public Plan {
		steps = steps == null ? List.of() : List.copyOf(steps);
		selectedAgents = selectedAgents == null ? List.of() : List.copyOf(selectedAgents);
		riskLevel = riskLevel == null ? RiskLevel.MEDIUM : riskLevel;
		createdAt = createdAt == null ? Instant.now() : createdAt;
	}

	public Plan withScore(double newScore) {
		return new Plan(planId, taskId, goal, steps, selectedAgents, estimatedCost, riskLevel,
			newScore, createdAt);
	}

	public Plan withSteps(List<PlanStep> newSteps) {
		return new Plan(planId, taskId, goal, newSteps, selectedAgents, estimatedCost,
			riskLevel, score, createdAt);
	}
}
