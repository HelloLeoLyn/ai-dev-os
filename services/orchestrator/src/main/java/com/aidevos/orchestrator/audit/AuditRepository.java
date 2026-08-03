package com.aidevos.orchestrator.audit;

import java.util.List;

public interface AuditRepository {
	EventRecord append(EventRecord event);
	EventRecord get(String id);
	List<EventRecord> query(EventQuery query);

	default long count(EventQuery query) {
		EventQuery effective = query == null ? EventQuery.all() : query;
		EventQuery unpaged = new EventQuery(effective.aggregateType(), effective.aggregateId(),
			effective.planRunId(), effective.stepRunId(), effective.attemptId(), effective.jobId(),
			effective.executionId(), effective.executionRecordId(), effective.invocationId(),
			effective.approvalId(), effective.eventTypes(), effective.occurredAfter(),
			effective.occurredBefore(), 0, EventQuery.MAX_LIMIT);
		return query(unpaged).size();
	}
}
