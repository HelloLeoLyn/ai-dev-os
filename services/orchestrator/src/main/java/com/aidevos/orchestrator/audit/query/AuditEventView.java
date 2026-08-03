package com.aidevos.orchestrator.audit.query;

import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import java.time.Instant;
import java.util.Map;

public record AuditEventView(
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
		int schemaVersion) {

	public static AuditEventView from(EventRecord event) {
		return new AuditEventView(event.id(), event.type(), event.occurredAt(), event.sequence(),
			event.aggregateType(), event.aggregateId(), event.fromStatus(), event.toStatus(),
			event.taskId(), event.planId(), event.planVersion(), event.planRunId(),
			event.stepRunId(), event.attemptId(), event.jobId(), event.executionId(),
			event.executionRecordId(), event.invocationId(), event.approvalId(), event.actorType(),
			event.actorId(), event.summary(), event.metadata(), event.schemaVersion());
	}
}
