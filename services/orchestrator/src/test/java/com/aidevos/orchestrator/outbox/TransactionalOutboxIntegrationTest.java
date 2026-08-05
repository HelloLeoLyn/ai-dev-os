package com.aidevos.orchestrator.outbox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.PostgresAuditRepository;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.persistence.postgresql.PostgresDocumentStore;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class TransactionalOutboxIntegrationTest {

	private static final Duration BACKOFF_BASE = Duration.ofSeconds(1);

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	private PGSimpleDataSource dataSource;
	private ObjectMapper mapper;
	private PostgresDocumentStore documents;
	private PostgresOutboxRepository outbox;
	private PostgresOutboxTransactions transactions;
	private AuditOutboxConsumer auditConsumer;
	private PostgresAuditRepository auditRepository;

	@BeforeEach
	void setUp() throws Exception {
		dataSource = new PGSimpleDataSource();
		dataSource.setUrl(POSTGRES.getJdbcUrl());
		dataSource.setUser(POSTGRES.getUsername());
		dataSource.setPassword(POSTGRES.getPassword());
		mapper = new ObjectMapper();
		documents = new PostgresDocumentStore(dataSource, mapper);
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
			statement.execute("TRUNCATE audit_outbox, audit_events, repository_documents RESTART IDENTITY");
		}
		outbox = new PostgresOutboxRepository(dataSource);
		transactions = new PostgresOutboxTransactions(dataSource);
		auditConsumer = new AuditOutboxConsumer(dataSource, mapper);
		auditRepository = new PostgresAuditRepository(dataSource, mapper, outbox, auditConsumer);
	}

	@Test
	void businessCommitPersistsStateAndOutboxTogether() {
		transactions.execute(() -> {
			documents.put("task", "task-1", task("task-1"), "secondary");
			outbox.enqueue("audit", "key-1", "{\"id\":\"event-1\"}");
			return null;
		});

		assertNotNull(documents.get("task", "task-1", TaskDefinition.class));
		assertNotNull(outbox.find("key-1"));
		assertEquals(1, outbox.pendingCount());
	}

	@Test
	void businessRollbackLeavesNeitherStateNorOutbox() {
		assertThrows(IllegalStateException.class, () -> transactions.execute(() -> {
			documents.put("task", "task-2", task("task-2"), "secondary");
			outbox.enqueue("audit", "key-2", "{\"id\":\"event-2\"}");
			throw new IllegalStateException("boom");
		}));

		assertNull(documents.get("task", "task-2", TaskDefinition.class));
		assertNull(outbox.find("key-2"));
		assertEquals(0, outbox.pendingCount());
	}

	@Test
	void publishFailureKeepsCommittedBusinessAndRecoversAfterBackoff() throws Exception {
		transactions.execute(() -> {
			documents.put("task", "task-3", task("task-3"), "secondary");
			outbox.enqueue("audit", "key-3", payload(event("event-3", "key-3")));
			return null;
		});

		TestClock clock = futureClock();
		OutboxRelay failingRelay = new OutboxRelay(outbox, transactions,
			List.of(new FailingConsumer("audit", 1)), clock, Duration.ofMillis(1), 10, 8,
			BACKOFF_BASE, Duration.ofMinutes(1));
		failingRelay.tick();

		assertNotNull(documents.get("task", "task-3", TaskDefinition.class));
		assertEquals(1, outbox.pendingCount());
		OutboxMessage message = outbox.find("key-3");
		assertNull(message.publishedAt());
		assertEquals(1, message.attempts());

		OutboxRelay recoveredRelay = new OutboxRelay(outbox, transactions, List.of(auditConsumer),
			clock, Duration.ofMillis(1), 10, 8, BACKOFF_BASE, Duration.ofMinutes(1));
		clock.advance(BACKOFF_BASE);
		recoveredRelay.tick();

		assertEquals(0, outbox.pendingCount());
		assertNotNull(outbox.find("key-3").publishedAt());
		assertEquals(1, countEvents("event-3"));
	}

	@Test
	void relayMaterializesAuditEventsExactlyOnce() {
		transactions.execute(() -> {
			outbox.enqueue("audit", "key-4", payload(event("event-4", "key-4")));
			return null;
		});

		OutboxRelay relay = new OutboxRelay(outbox, transactions, List.of(auditConsumer),
			futureClock(), Duration.ofMillis(1), 10, 8, BACKOFF_BASE, Duration.ofMinutes(1));
		relay.tick();
		assertEquals(1, countEvents("event-4"));

		relay.tick();
		assertEquals(1, countEvents("event-4"));
		assertEquals(0, outbox.pendingCount());
	}

	@Test
	void appendInsideBusinessTransactionEnqueuesAndRelayPublishesAfterCommit() {
		transactions.execute(() -> {
			documents.put("task", "task-5", task("task-5"), "secondary");
			auditRepository.append(event("event-5", "key-5"));
			return null;
		});

		assertEquals(1, outbox.pendingCount());
		assertEquals(0, countEvents("event-5"));

		OutboxRelay relay = new OutboxRelay(outbox, transactions, List.of(auditConsumer),
			futureClock(), Duration.ofMillis(1), 10, 8, BACKOFF_BASE, Duration.ofMinutes(1));
		relay.tick();

		assertEquals(1, countEvents("event-5"));
		assertEquals(0, outbox.pendingCount());
		EventRecord published = auditRepository.get("event-5");
		assertNotNull(published);
		assertTrue(published.sequence() > 0);
	}

	@Test
	void standaloneAppendPublishesImmediately() {
		auditRepository.append(event("event-6", "key-6"));

		assertEquals(1, countEvents("event-6"));
		assertEquals(0, outbox.pendingCount());
		assertTrue(auditRepository.get("event-6").sequence() > 0);
	}

	@Test
	void rollbackInsideAuditAppendLeavesNoOutbox() {
		assertThrows(IllegalStateException.class, () -> transactions.execute(() -> {
			documents.put("task", "task-7", task("task-7"), "secondary");
			auditRepository.append(event("event-7", "key-7"));
			throw new IllegalStateException("boom");
		}));

		assertNull(documents.get("task", "task-7", TaskDefinition.class));
		assertNull(outbox.find("key-7"));
		assertEquals(0, outbox.pendingCount());
		assertEquals(0, countEvents("event-7"));
	}

	@Test
	void crashWindowRecoversWithoutDuplicatePublish() {
		transactions.execute(() -> {
			outbox.enqueue("audit", "key-8", payload(event("event-8", "key-8")));
			return null;
		});

		TestClock clock = futureClock();
		OutboxRelay crashed = new OutboxRelay(outbox, transactions,
			List.of(new FailingConsumer("audit", 1)), clock, Duration.ofMillis(1), 10, 8,
			BACKOFF_BASE, Duration.ofMinutes(1));
		crashed.tick();
		assertEquals(0, countEvents("event-8"));

		OutboxRelay restarted = new OutboxRelay(outbox, transactions, List.of(auditConsumer),
			clock, Duration.ofMillis(1), 10, 8, BACKOFF_BASE, Duration.ofMinutes(1));
		clock.advance(BACKOFF_BASE);
		restarted.tick();

		assertEquals(1, countEvents("event-8"));
		restarted.tick();
		assertEquals(1, countEvents("event-8"));
	}


	private TestClock futureClock() {
		return new TestClock(Instant.now().plusSeconds(60));
	}
	private EventRecord event(String id, String key) {
		return new EventRecord(id, EventType.JOB_SUBMITTED,
			Instant.parse("2026-08-04T08:00:01Z"), 0, "job", "job-1", null, "QUEUED",
			"task-1", null, null, null, null, null, "job-1", null, null, null, null,
			"SYSTEM", "test", "event", Map.of(), key, 1);
	}

	private String payload(EventRecord event) {
		try {
			return mapper.writeValueAsString(event);
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

	private long countEvents(String id) {
		try (Connection connection = dataSource.getConnection(); PreparedStatement statement =
				connection.prepareStatement("SELECT COUNT(*) FROM audit_events WHERE id=?")) {
			statement.setString(1, id);
			try (ResultSet result = statement.executeQuery()) {
				result.next();
				return result.getLong(1);
			}
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

	private TaskDefinition task(String id) {
		TaskDefinition task = new TaskDefinition();
		task.setId(id);
		return task;
	}

	static final class FailingConsumer implements OutboxConsumer {
		private final String topic;
		private int failuresRemaining;

		FailingConsumer(String topic, int failures) {
			this.topic = topic;
			this.failuresRemaining = failures;
		}

		@Override
		public String topic() { return topic; }

		@Override
		public void consume(OutboxMessage message) {
			if (failuresRemaining > 0) {
				failuresRemaining--;
				throw new IllegalStateException("consumer failure");
			}
		}
	}

	static final class TestClock extends Clock {
		private Instant instant;

		TestClock(Instant instant) {
			this.instant = instant;
		}

		void advance(Duration duration) {
			instant = instant.plus(duration);
		}

		@Override
		public Instant instant() { return instant; }

		@Override
		public ZoneId getZone() { return ZoneOffset.UTC; }

		@Override
		public Clock withZone(ZoneId zone) { return this; }
	}
}
