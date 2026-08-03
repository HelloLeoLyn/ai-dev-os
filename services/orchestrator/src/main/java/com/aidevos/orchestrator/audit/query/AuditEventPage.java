package com.aidevos.orchestrator.audit.query;

import java.util.List;

public record AuditEventPage(int offset, int limit, int count, long totalCount, boolean hasMore,
		List<AuditEventView> events) {
	public AuditEventPage {
		events = List.copyOf(events);
	}
}
