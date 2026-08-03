package com.aidevos.orchestrator.audit;

import java.util.List;

public interface AuditRepository {
	EventRecord append(EventRecord event);
	EventRecord get(String id);
	List<EventRecord> query(EventQuery query);
}
