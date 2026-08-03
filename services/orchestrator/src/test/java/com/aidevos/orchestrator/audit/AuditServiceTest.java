package com.aidevos.orchestrator.audit;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuditServiceTest {
	@Test
	void repositoryFailureDoesNotEscapeRecordBoundary() {
		AuditRepository broken = new AuditRepository() {
			public EventRecord append(EventRecord event) { throw new IllegalStateException("down"); }
			public EventRecord get(String id) { return null; }
			public List<EventRecord> query(EventQuery query) { return List.of(); }
		};
		AuditService service = new AuditService(broken);

		assertTrue(service.record(AuditRepositoryContract.event("event-1", "key-1",
			EventType.JOB_STARTED, Instant.parse("2026-08-03T02:00:01Z"), "job-1", "run-1"))
			.isEmpty());
	}
}
