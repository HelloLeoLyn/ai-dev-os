package com.aidevos.orchestrator.timeline;

import java.util.List;

/**
 * Unified timeline for a Task, PlanRun, StepRun, Job or Execution id.
 */
public record UnifiedTimeline(
		String scopeType,
		String scopeId,
		List<TimelineEventDTO> events) {

	public UnifiedTimeline {
		events = List.copyOf(events);
	}
}
