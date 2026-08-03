package com.aidevos.orchestrator.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

abstract class AuditRepositoryContract {
	abstract AuditRepository repository();

	@Test
	void appendsGetsQueriesAndOrdersEvents() {
		EventRecord later = event("event-2", "key-2", EventType.JOB_SUCCEEDED,
			Instant.parse("2026-08-03T02:00:02Z"), "job-1", "run-1");
		EventRecord earlier = event("event-1", "key-1", EventType.JOB_STARTED,
			Instant.parse("2026-08-03T02:00:01Z"), "job-1", "run-1");

		EventRecord storedLater = repository().append(later);
		EventRecord storedEarlier = repository().append(earlier);

		assertTrue(storedLater.sequence() > 0);
		assertTrue(storedEarlier.sequence() > storedLater.sequence());
		assertEquals(storedEarlier, repository().get("event-1"));
		assertEquals(List.of("event-1", "event-2"), repository().query(query("job-1", "run-1", 0, 10))
			.stream().map(EventRecord::id).toList());
		assertEquals(List.of("event-2"), repository().query(query("job-1", "run-1", 1, 1))
			.stream().map(EventRecord::id).toList());
	}

	@Test
	void appendIsIdempotentAndDoesNotOverwriteFirstEvent() {
		EventRecord first = event("event-1", "same-key", EventType.JOB_STARTED,
			Instant.parse("2026-08-03T02:00:01Z"), "job-1", "run-1");
		EventRecord duplicate = event("event-2", "same-key", EventType.JOB_FAILED,
			Instant.parse("2026-08-03T02:00:02Z"), "job-1", "run-1");

		EventRecord stored = repository().append(first);
		EventRecord repeated = repository().append(duplicate);

		assertEquals(stored, repeated);
		assertEquals("event-1", repeated.id());
		assertEquals(EventType.JOB_STARTED, repeated.type());
		assertEquals(1, repository().query(EventQuery.all()).size());
	}

	@Test
	void rejectsReplacementByEventId() {
		repository().append(event("event-1", "key-1", EventType.JOB_STARTED,
			Instant.parse("2026-08-03T02:00:01Z"), "job-1", "run-1"));
		assertThrows(IllegalStateException.class, () -> repository().append(event("event-1", "key-2",
			EventType.JOB_FAILED, Instant.parse("2026-08-03T02:00:02Z"), "job-1", "run-1")));
	}

	private EventQuery query(String jobId, String planRunId, int offset, int limit) {
		return new EventQuery(null, null, planRunId, null, null, jobId, null, null, null,
			null, Set.of(EventType.JOB_STARTED, EventType.JOB_SUCCEEDED), null, null, offset, limit);
	}

	static EventRecord event(String id, String key, EventType type, Instant time, String jobId,
			String planRunId) {
		return new EventRecord(id, type, time, 0, "job", jobId, null, type.name(), "task-1",
			"plan-1", 1, planRunId, "step-run-1", "attempt-1", jobId, "execution-1",
			"record-1", "invocation-1", "approval-1", "SYSTEM", "test", "summary",
			Map.of("nested", Map.of("phase", "7-B1"), "count", 2), key, 1);
	}
}
