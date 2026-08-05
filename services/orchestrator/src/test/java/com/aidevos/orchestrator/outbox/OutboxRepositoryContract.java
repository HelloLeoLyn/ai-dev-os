package com.aidevos.orchestrator.outbox;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

abstract class OutboxRepositoryContract {

	abstract OutboxRepository repository();

	abstract Instant now();

	@Test
	void enqueueIsIdempotentAndCountsPending() {
		OutboxMessage first = repository().enqueue("audit", "key-1", "{\"id\":\"event-1\"}");
		OutboxMessage repeated = repository().enqueue("audit", "key-1", "{\"id\":\"event-2\"}");

		assertEquals(first, repeated);
		assertEquals("key-1", repeated.idempotencyKey());
		assertEquals(1, repository().pendingCount());
		assertEquals(0, repository().deadLetteredCount());
	}

	@Test
	void claimReturnsPendingInCreatedOrderRespectingLimit() {
		repository().enqueue("audit", "key-1", "{}");
		repository().enqueue("audit", "key-2", "{}");
		repository().enqueue("audit", "key-3", "{}");

		List<OutboxMessage> first = repository().claimPending(now(), 2);
		assertEquals(List.of("key-1", "key-2"),
			first.stream().map(OutboxMessage::idempotencyKey).toList());

		// Claiming is a selection: rows stay pending until published.
		repository().markPublished("key-1");
		repository().markPublished("key-2");
		List<OutboxMessage> second = repository().claimPending(now(), 2);
		assertEquals(List.of("key-3"),
			second.stream().map(OutboxMessage::idempotencyKey).toList());
	}

	@Test
	void publishedMessagesAreNoLongerClaimed() {
		repository().enqueue("audit", "key-1", "{}");
		assertTrue(repository().markPublished("key-1"));

		assertTrue(repository().claimPending(now(), 10).isEmpty());
		assertEquals(0, repository().pendingCount());
		OutboxMessage message = repository().find("key-1");
		assertNotNull(message.publishedAt());
		assertEquals(1, message.attempts());
	}

	@Test
	void failedMessagesRespectBackoffWindow() {
		repository().enqueue("audit", "key-1", "{}");
		Instant backoffUntil = now().plusSeconds(30);
		assertTrue(repository().markFailed("key-1", "boom", backoffUntil));

		assertTrue(repository().claimPending(now(), 10).isEmpty());
		assertEquals(1, repository().pendingCount());

		List<OutboxMessage> due = repository().claimPending(backoffUntil.plusSeconds(1), 10);
		assertEquals(List.of("key-1"), due.stream().map(OutboxMessage::idempotencyKey).toList());
		assertEquals("boom", due.getFirst().lastError());
		assertEquals(1, due.getFirst().attempts());
	}

	@Test
	void deadLetteredMessagesAreNoLongerClaimed() {
		repository().enqueue("audit", "key-1", "{}");
		assertTrue(repository().markDeadLettered("key-1", "poison"));

		assertTrue(repository().claimPending(now(), 10).isEmpty());
		assertEquals(0, repository().pendingCount());
		assertEquals(1, repository().deadLetteredCount());
		OutboxMessage message = repository().find("key-1");
		assertNotNull(message.deadLetteredAt());
		assertEquals("poison", message.lastError());
	}

	@Test
	void findReturnsEnqueuedMessageOrNull() {
		assertNull(repository().find("missing"));
		OutboxMessage message = repository().enqueue("audit", "key-1", "{\"a\":1}");
		assertEquals(message, repository().find("key-1"));
		assertEquals("audit", message.topic());
		assertTrue(message.payload().contains("\"a\""));
	}
}
