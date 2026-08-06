package com.aidevos.orchestrator.dashboard;

import java.time.Instant;

/**
 * Read-only job view for the dashboard monitoring pages.
 */
public record JobSummaryDTO(
		String jobId,
		String status,
		int priority,
		String leaseOwner,
		Instant createdAt,
		Instant updatedAt) {
}
