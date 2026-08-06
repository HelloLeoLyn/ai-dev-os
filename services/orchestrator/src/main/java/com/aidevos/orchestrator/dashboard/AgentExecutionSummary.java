package com.aidevos.orchestrator.dashboard;

import java.time.Instant;

/**
 * Read-only execution record view for an agent.
 */
public record AgentExecutionSummary(
		String executionId,
		String jobId,
		String status,
		Instant startedAt,
		Instant completedAt,
		String message) {
}
