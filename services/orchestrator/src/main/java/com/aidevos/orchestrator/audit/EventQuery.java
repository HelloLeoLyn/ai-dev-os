package com.aidevos.orchestrator.audit;

import java.time.Instant;
import java.util.Set;

public record EventQuery(
		String aggregateType,
		String aggregateId,
		String planRunId,
		String stepRunId,
		String attemptId,
		String jobId,
		String executionId,
		String executionRecordId,
		String invocationId,
		String approvalId,
		Set<EventType> eventTypes,
		Instant occurredAfter,
		Instant occurredBefore,
		int offset,
		int limit) {

	public static final int DEFAULT_LIMIT = 100;
	public static final int MAX_LIMIT = 1000;

	public EventQuery {
		eventTypes = eventTypes == null ? Set.of() : Set.copyOf(eventTypes);
		if (offset < 0) throw new IllegalArgumentException("Event query offset cannot be negative");
		if (limit < 1 || limit > MAX_LIMIT) {
			throw new IllegalArgumentException("Event query limit must be between 1 and " + MAX_LIMIT);
		}
		if (occurredAfter != null && occurredBefore != null
				&& occurredAfter.isAfter(occurredBefore)) {
			throw new IllegalArgumentException("Event query time range is invalid");
		}
	}

	public static EventQuery all() {
		return new EventQuery(null, null, null, null, null, null, null, null, null, null,
			Set.of(), null, null, 0, DEFAULT_LIMIT);
	}

	boolean matches(EventRecord event) {
		return same(aggregateType, event.aggregateType()) && same(aggregateId, event.aggregateId())
			&& same(planRunId, event.planRunId()) && same(stepRunId, event.stepRunId())
			&& same(attemptId, event.attemptId()) && same(jobId, event.jobId())
			&& same(executionId, event.executionId())
			&& same(executionRecordId, event.executionRecordId())
			&& same(invocationId, event.invocationId()) && same(approvalId, event.approvalId())
			&& (eventTypes.isEmpty() || eventTypes.contains(event.type()))
			&& (occurredAfter == null || !event.occurredAt().isBefore(occurredAfter))
			&& (occurredBefore == null || !event.occurredAt().isAfter(occurredBefore));
	}

	private boolean same(String expected, String actual) {
		return expected == null || expected.equals(actual);
	}
}
