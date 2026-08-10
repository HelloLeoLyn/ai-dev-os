package com.aidevos.orchestrator.metrics.agent;

import java.time.Instant;

/**
 * Aggregated execution metrics for one agent, derived on demand from the
 * existing ExecutionRecords, audit events, repair tasks and change sets. Never
 * stored as business data.
 */
public record AgentMetrics(
		String agentId,
		String agentName,
		int taskCount,
		int successCount,
		int failedCount,
		int retryCount,
		long averageDuration,
		Instant lastExecutedAt,
		int repairCount,
		int changeCount,
		long tokenCount,
		double estimatedCost,
		double successCost) {

	/**
	 * Backward-compatible constructor without the usage figures (token
	 * consumption, estimated cost and success cost default to zero).
	 */
	public AgentMetrics(String agentId, String agentName, int taskCount, int successCount,
			int failedCount, int retryCount, long averageDuration, Instant lastExecutedAt,
			int repairCount, int changeCount) {
		this(agentId, agentName, taskCount, successCount, failedCount, retryCount,
			averageDuration, lastExecutedAt, repairCount, changeCount, 0, 0.0, 0.0);
	}
}
