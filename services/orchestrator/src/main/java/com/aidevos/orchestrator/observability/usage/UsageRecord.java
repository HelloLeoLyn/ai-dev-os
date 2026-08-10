package com.aidevos.orchestrator.observability.usage;

import java.time.Instant;

/**
 * One model usage record: token consumption and estimated cost for a task /
 * agent. Real model token collection is out of scope; callers record usage
 * through the service interface.
 */
public record UsageRecord(
		String usageId,
		String taskId,
		String projectId,
		String agentType,
		String model,
		long inputTokens,
		long outputTokens,
		long totalTokens,
		double estimatedCost,
		Instant createdAt) {
}
