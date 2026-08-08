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
		int changeCount) {
}
