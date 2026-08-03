package com.aidevos.orchestrator.audit.timeline;

import com.aidevos.orchestrator.audit.query.AuditEventView;
import java.util.List;

public record ExecutionTimeline(
		TimelineScopeType scopeType,
		String scopeId,
		int offset,
		int limit,
		int count,
		long totalCount,
		boolean hasMore,
		List<AuditEventView> events) {
	public ExecutionTimeline {
		events = List.copyOf(events);
	}
}
