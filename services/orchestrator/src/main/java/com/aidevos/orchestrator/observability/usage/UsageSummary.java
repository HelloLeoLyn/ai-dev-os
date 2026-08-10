package com.aidevos.orchestrator.observability.usage;

/**
 * Aggregated token/cost figures for a task, project or agent.
 */
public record UsageSummary(
		long recordCount,
		long inputTokens,
		long outputTokens,
		long totalTokens,
		double estimatedCost) {

	public static UsageSummary empty() {
		return new UsageSummary(0, 0, 0, 0, 0.0);
	}
}
