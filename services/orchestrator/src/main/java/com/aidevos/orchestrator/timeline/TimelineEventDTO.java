package com.aidevos.orchestrator.timeline;

import java.time.Instant;

/**
 * Unified timeline event view. sourceType is one of PLAN_RUN, STEP_RUN, JOB,
 * EXECUTION, TASK or AUDIT; status carries the to/from status of the event.
 */
public record TimelineEventDTO(
		String eventId,
		String eventType,
		String sourceType,
		String sourceId,
		String status,
		String message,
		Instant timestamp) {
}
