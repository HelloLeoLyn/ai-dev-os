package com.aidevos.orchestrator.dashboard;

import java.time.Instant;

/**
 * Read-only execution view for the dashboard monitoring pages.
 */
public record ExecutionSummaryDTO(
		String executionId,
		String jobId,
		String status,
		int attempt,
		String failureReason,
		Instant createdAt) {
}
