package com.aidevos.orchestrator.audit.query;

import java.util.List;

public record AuditEventPage(int offset, int limit, int count, List<AuditEventView> events) {
	public AuditEventPage {
		events = List.copyOf(events);
	}
}
