package com.aidevos.orchestrator.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutboxRelayTest {

	private static final Instant START = Instant.parse("2026-08-04T08:00:00Z");
	private static final Duration BASE = Duration.ofSeconds(1);
	private static final Duration MAX = Duration.ofMinutes(1);

	private TestClock clock;
	private InMemoryOutboxRepository outbox;

	@BeforeEach
	void setUp() {
		clock = new TestClock(START);
		outbox = new InMemoryOutboxRepository(clock);
	}

	@Test
	void publishesPendingMessagesThroughTopicConsumer() {
		RecordingConsumer consumer = new RecordingConsumer("audit");
		outbox.enqueue("audit", "key-1", "payload-1");
		OutboxRelay relay = relay(List.of(consumer), 10, 8);

		relay.tick();

		assertEquals(List.of("key-1"), consumer.seenKeys());
		OutboxMessage message = outbox.find("key-1");
		assertNotNull(message.publishedAt());
		assertEquals(1, message.attempts());
		assertEquals(0, outbox.pendingCount());
	}

	@Test
	void retriesWithExponentialBackoffThenPublishes() {
		RecordingConsumer consumer = new RecordingConsumer("audit", 2);
		outbox.enqueue("audit", "key-1", "payload-1");
		OutboxRelay relay = relay(List.of(consumer), 10, 8);

		relay.tick();
		OutboxMessage firstFailure = outbox.find("key-1");
		assertNull(firstFailure.publishedAt());
		assertEquals(1, firstFailure.attempts());
		assertEquals(START.plus(BASE), firstFailure.nextAttemptAt());
		assertTrue(outbox.claimPending(START.plusMillis(999), 10).isEmpty());
		assertFalse(outbox.claimPending(START.plusSeconds(1), 10).isEmpty());

		clock.advance(BASE);
		relay.tick();
		OutboxMessage secondFailure = outbox.find("key-1");
		assertEquals(2, secondFailure.attempts());
		assertEquals(START.plusSeconds(3), secondFailure.nextAttemptAt());

		clock.advance(Duration.ofSeconds(2));
		relay.tick();

		assertEquals(1, consumer.seenKeys().size());
		assertEquals(List.of("key-1"), consumer.seenKeys());
		assertNotNull(outbox.find("key-1").publishedAt());
		assertEquals(0, outbox.pendingCount());
	}

	@Test
	void deadLettersAfterMaxAttempts() {
		RecordingConsumer consumer = new RecordingConsumer("audit", Integer.MAX_VALUE);
		outbox.enqueue("audit", "key-1", "payload-1");
		OutboxRelay relay = relay(List.of(consumer), 10, 3);

		relay.tick();
		clock.advance(BASE);
		relay.tick();
		clock.advance(Duration.ofSeconds(2));
		relay.tick();

		assertEquals(1, outbox.deadLetteredCount());
		assertEquals(0, outbox.pendingCount());
		OutboxMessage message = outbox.find("key-1");
		assertNotNull(message.deadLetteredAt());
		assertEquals(3, message.attempts());

		clock.advance(Duration.ofMinutes(5));
		relay.tick();
		assertEquals(1, outbox.deadLetteredCount());
	}

	@Test
	void deadLettersMessageWithoutConsumer() {
		outbox.enqueue("unknown-topic", "key-1", "payload-1");
		OutboxRelay relay = relay(List.of(new RecordingConsumer("audit")), 10, 3);

		relay.tick();
		clock.advance(BASE);
		relay.tick();
		clock.advance(Duration.ofSeconds(2));
		relay.tick();

		assertEquals(1, outbox.deadLetteredCount());
		assertEquals(0, outbox.pendingCount());
	}

	@Test
	void emptyQueueTickIsHarmless() {
		OutboxRelay relay = relay(List.of(new RecordingConsumer("audit")), 10, 3);
		relay.tick();
		assertEquals(0, outbox.pendingCount());
	}

	private OutboxRelay relay(List<OutboxConsumer> consumers, int batchSize, int maxAttempts) {
		return new OutboxRelay(outbox, new InMemoryOutboxTransactions(), consumers, clock,
			Duration.ofMillis(1), batchSize, maxAttempts, BASE, MAX);
	}

	static final class RecordingConsumer implements OutboxConsumer {
		private final String topic;
		private final List<OutboxMessage> seen = new ArrayList<>();
		private int failuresRemaining;

		RecordingConsumer(String topic) {
			this(topic, 0);
		}

		RecordingConsumer(String topic, int failures) {
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
			seen.add(message);
		}

		List<String> seenKeys() {
			return seen.stream().map(OutboxMessage::idempotencyKey).toList();
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
