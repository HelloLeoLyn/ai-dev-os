package com.aidevos.orchestrator.audit;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class EventRecordJsonTest {
	@Test
	void roundTripsJsonPayload() throws Exception {
		EventRecord source = AuditRepositoryContract.event("event-1", "key-1",
			EventType.EXECUTION_RECORD_SAVED, Instant.parse("2026-08-03T02:00:01Z"),
			"job-1", "run-1").withSequence(42);
		ObjectMapper mapper = new ObjectMapper();

		EventRecord restored = mapper.readValue(mapper.writeValueAsString(source), EventRecord.class);

		assertEquals(source, restored);
		assertEquals("7-B1", ((java.util.Map<?, ?>) restored.metadata().get("nested")).get("phase"));
	}
}
