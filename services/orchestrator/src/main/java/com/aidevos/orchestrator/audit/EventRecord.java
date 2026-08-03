package com.aidevos.orchestrator.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record EventRecord(
		String id,
		EventType type,
		Instant occurredAt,
		long sequence,
		String aggregateType,
		String aggregateId,
		String fromStatus,
		String toStatus,
		String taskId,
		String planId,
		Integer planVersion,
		String planRunId,
		String stepRunId,
		String attemptId,
		String jobId,
		String executionId,
		String executionRecordId,
		String invocationId,
		String approvalId,
		String actorType,
		String actorId,
		String summary,
		Map<String, Object> metadata,
		String idempotencyKey,
		int schemaVersion) {

	public EventRecord {
		id = requireText(id, "Event id is required");
		if (type == null) throw new IllegalArgumentException("Event type is required");
		if (occurredAt == null) throw new IllegalArgumentException("Event time is required");
		aggregateType = requireText(aggregateType, "Aggregate type is required");
		aggregateId = requireText(aggregateId, "Aggregate id is required");
		idempotencyKey = requireText(idempotencyKey, "Idempotency key is required");
		if (schemaVersion < 1) throw new IllegalArgumentException("Schema version must be positive");
		metadata = metadata == null ? Map.of()
			: Map.copyOf(new LinkedHashMap<>(metadata));
	}

	public EventRecord withSequence(long assignedSequence) {
		return new EventRecord(id, type, occurredAt, assignedSequence, aggregateType, aggregateId,
			fromStatus, toStatus, taskId, planId, planVersion, planRunId, stepRunId, attemptId,
			jobId, executionId, executionRecordId, invocationId, approvalId, actorType, actorId,
			summary, metadata, idempotencyKey, schemaVersion);
	}

	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
		return value;
	}
}
