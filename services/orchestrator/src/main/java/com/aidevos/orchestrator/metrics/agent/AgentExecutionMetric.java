package com.aidevos.orchestrator.metrics.agent;

import java.time.Instant;

/**
 * One agent execution as seen by the metrics layer: duration, status and time
 * derived from the underlying ExecutionRecord.
 */
public record AgentExecutionMetric(
		String taskId,
		String agentId,
		String executionId,
		long durationMillis,
		String status,
		Instant createdAt) {
}
