package com.aidevos.orchestrator.memory;

import java.util.List;

import com.aidevos.orchestrator.memory.search.MemoryMatch;

/**
 * Memory hints injected into an agent execution context before the graph
 * runs: similar historical tasks, known solutions, warnings (unresolved
 * issues) and actionable recommendations. Agents (e.g. the Hermes planner)
 * consume these to carry historical experience into planning.
 */
public class MemoryContext {

	private List<MemoryMatch> similarTasks = List.of();
	private List<MemoryMatch> solutions = List.of();
	private List<String> warnings = List.of();
	private List<String> recommendations = List.of();

	public MemoryContext() {
	}

	public MemoryContext(List<MemoryMatch> similarTasks, List<MemoryMatch> solutions,
			List<String> warnings, List<String> recommendations) {
		this.similarTasks = similarTasks == null ? List.of() : List.copyOf(similarTasks);
		this.solutions = solutions == null ? List.of() : List.copyOf(solutions);
		this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
		this.recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
	}

	public List<MemoryMatch> getSimilarTasks() {
		return similarTasks;
	}

	public void setSimilarTasks(List<MemoryMatch> similarTasks) {
		this.similarTasks = similarTasks == null ? List.of() : List.copyOf(similarTasks);
	}

	public List<MemoryMatch> getSolutions() {
		return solutions;
	}

	public void setSolutions(List<MemoryMatch> solutions) {
		this.solutions = solutions == null ? List.of() : List.copyOf(solutions);
	}

	public List<String> getWarnings() {
		return warnings;
	}

	public void setWarnings(List<String> warnings) {
		this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
	}

	public List<String> getRecommendations() {
		return recommendations;
	}

	public void setRecommendations(List<String> recommendations) {
		this.recommendations = recommendations == null ? List.of()
			: List.copyOf(recommendations);
	}

	public boolean isEmpty() {
		return similarTasks.isEmpty() && solutions.isEmpty()
			&& warnings.isEmpty() && recommendations.isEmpty();
	}
}
