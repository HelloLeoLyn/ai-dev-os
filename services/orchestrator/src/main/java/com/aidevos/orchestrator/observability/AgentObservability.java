package com.aidevos.orchestrator.observability;

/**
 * Agent-level observability: execution counts, success rate, average duration
 * and the aggregated token/cost usage for one agent type.
 */
public record AgentObservability(
		String agentType,
		int executionCount,
		int successCount,
		int failedCount,
		double successRate,
		long averageDurationMillis,
		long totalTokens,
		double estimatedCost) {
}
