package com.aidevos.orchestrator.observability;

/**
 * Project-level observability: task counts, success/failure rates, average
 * execution duration and the aggregated token/cost usage.
 */
public record ProjectObservability(
		String projectId,
		int taskCount,
		int successCount,
		int failedCount,
		double successRate,
		double failureRate,
		long averageDurationMillis,
		long totalTokens,
		double estimatedCost) {
}
