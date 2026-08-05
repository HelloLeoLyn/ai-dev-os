package com.aidevos.orchestrator.outbox;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.aidevos.orchestrator.persistence.postgresql.PostgresDocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Phase 8-F outbox recovery validation: while the relay is stopped, rows must
 * stay pending; a restarted relay drains the backlog exactly once, and a relay
 * that crashed mid-drain leaves the backlog recoverable without duplicates.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresOutboxRelayRecoveryTest {

	private static final Duration BASE = Duration.ofSeconds(1);
	private static final Duration MAX = Duration.ofMinutes(1);

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	private PGSimpleDataSource dataSource;
	private PostgresOutboxRepository outbox;
	private PostgresOutboxTransactions transactions;
	private List<String> published;

	@BeforeEach
	void setUp() throws Exception {
		dataSource = new PGSimpleDataSource();
		dataSource.setUrl(POSTGRES.getJdbcUrl());
		dataSource.setUser(POSTGRES.getUsername());
		dataSource.setPassword(POSTGRES.getPassword());
		new PostgresDocumentStore(dataSource, new ObjectMapper());
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("TRUNCATE audit_outbox RESTART IDENTITY");
		}
		outbox = new PostgresOutboxRepository(dataSource);
		transactions = new PostgresOutboxTransactions(dataSource);
		published = new ArrayList<>();
	}

	@Test
	void stoppedRelayKeepsRowsPendingUntilRestarted() {
		for (int index = 1; index <= 5; index++) {
			outbox.enqueue("audit", "key-" + index, "{\"index\":" + index + "}");
		}
		assertEquals(5, outbox.pendingCount());
		assertEquals(0, published.size());

		OutboxRelay relay = relay(new RecordingConsumer());
		relay.tick();

		assertEquals(0, outbox.pendingCount());
		assertEquals(Set.of("key-1", "key-2", "key-3", "key-4", "key-5"),
			new HashSet<>(published));
		for (String key : List.of("key-1", "key-2", "key-3", "key-4", "key-5")) {
			assertNotNull(outbox.find(key).publishedAt());
		}

		relay.tick();
		assertEquals(5, published.size());
	}

	@Test
	void crashedRelayLeavesBacklogAndNewRelayRecoversExactlyOnce() {
		for (int index = 1; index <= 3; index++) {
			outbox.enqueue("audit", "key-" + index, "{\"index\":" + index + "}");
		}
		MutableClock clock = new MutableClock(Instant.now().plusSeconds(60));

		OutboxRelay crashed = relay(clock, new FailingConsumer());
		crashed.tick();
		assertEquals(3, outbox.pendingCount());
		assertEquals(0, published.size());

		clock.advance(BASE);
		OutboxRelay restarted = relay(clock, new RecordingConsumer());
		restarted.tick();

		assertEquals(0, outbox.pendingCount());
		assertEquals(3, published.size());
		restarted.tick();
		assertEquals(3, published.size());
	}

	private OutboxRelay relay(OutboxConsumer consumer) {
		return relay(new MutableClock(Instant.now().plusSeconds(60)), consumer);
	}

	private OutboxRelay relay(MutableClock clock, OutboxConsumer consumer) {
		return new OutboxRelay(outbox, transactions, List.of(consumer), clock,
			Duration.ofMillis(1), 10, 8, BASE, MAX);
	}

	private final class RecordingConsumer implements OutboxConsumer {
		@Override
		public String topic() {
			return "audit";
		}

		@Override
		public void consume(OutboxMessage message) {
			published.add(message.idempotencyKey());
		}
	}

	private static final class FailingConsumer implements OutboxConsumer {
		@Override
		public String topic() {
			return "audit";
		}

		@Override
		public void consume(OutboxMessage message) {
			throw new IllegalStateException("relay crashed");
		}
	}

	private static final class MutableClock extends Clock {
		private Instant instant;

		MutableClock(Instant instant) {
			this.instant = instant;
		}

		void advance(Duration duration) {
			instant = instant.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
