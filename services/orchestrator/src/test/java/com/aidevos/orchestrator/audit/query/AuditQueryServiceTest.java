package com.aidevos.orchestrator.audit.query;

import com.aidevos.orchestrator.audit.*;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AuditQueryServiceTest {
	@Test
	void filtersByCorrelationTypeTimeAndPagination() {
		InMemoryAuditRepository repository = new InMemoryAuditRepository();
		repository.append(event("1", EventType.JOB_STARTED, "run-1", "job-1", "exec-1",
			"2026-08-03T01:00:00Z"));
		repository.append(event("2", EventType.JOB_SUCCEEDED, "run-1", "job-1", "exec-1",
			"2026-08-03T01:00:01Z"));
		repository.append(event("3", EventType.JOB_FAILED, "run-2", "job-2", "exec-2",
			"2026-08-03T01:00:02Z"));
		EventQuery query = new EventQuery(null, null, "run-1", null, null, "job-1", "exec-1",
			null, null, null, Set.of(EventType.JOB_STARTED, EventType.JOB_SUCCEEDED),
			Instant.parse("2026-08-03T01:00:00Z"), Instant.parse("2026-08-03T01:00:01Z"), 1, 1);

		AuditEventPage page = new AuditQueryService(repository).query(query);

		assertEquals(1, page.offset());
		assertEquals(1, page.limit());
		assertEquals(1, page.count());
		assertEquals(EventType.JOB_SUCCEEDED, page.events().getFirst().type());
	}

	public static EventRecord event(String id, EventType type, String planRunId, String jobId,
			String executionId, String time) {
		return new EventRecord(id, type, Instant.parse(time), 0, "test", id, null, null,
			"task-1", "plan-1", 1, planRunId, null, null, jobId, executionId, null, null,
			null, "SYSTEM", "test", type.name(), Map.of("safe", true), "key-" + id, 1);
	}
}
