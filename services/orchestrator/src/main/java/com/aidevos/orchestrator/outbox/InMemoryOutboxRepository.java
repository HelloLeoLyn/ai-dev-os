package com.aidevos.orchestrator.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * In-memory outbox used for contract tests and the in-memory persistence mode.
 * The audit path keeps writing directly through InMemoryAuditRepository, so
 * this repository exists for relay behaviour tests and profile compatibility.
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory",
	matchIfMissing = true)
public class InMemoryOutboxRepository implements OutboxRepository {

	private static final Comparator<OutboxMessage> ORDER = Comparator
		.comparing(OutboxMessage::createdAt).thenComparing(OutboxMessage::idempotencyKey);

	private final Map<String, OutboxMessage> messages = new LinkedHashMap<>();
	private final Clock clock;

	public InMemoryOutboxRepository() {
		this(Clock.systemUTC());
	}

	InMemoryOutboxRepository(Clock clock) {
		this.clock = clock;
	}

	@Override
	public synchronized OutboxMessage enqueue(String topic, String idempotencyKey, String payload) {
		OutboxMessage existing = messages.get(idempotencyKey);
		if (existing != null) {
			return existing;
		}
		Instant now = clock.instant();
		OutboxMessage message = new OutboxMessage(topic, idempotencyKey, payload, now, 0, now,
			null, null, null);
		messages.put(idempotencyKey, message);
		return message;
	}

	@Override
	public synchronized OutboxMessage find(String idempotencyKey) {
		return messages.get(idempotencyKey);
	}

	@Override
	public synchronized List<OutboxMessage> claimPending(Instant now, int limit) {
		return messages.values().stream().filter(message -> message.pending(now))
			.sorted(ORDER).limit(limit).toList();
	}

	@Override
	public synchronized boolean markPublished(String idempotencyKey) {
		OutboxMessage message = messages.get(idempotencyKey);
		if (message == null) {
			return false;
		}
		messages.put(idempotencyKey, new OutboxMessage(message.topic(), message.idempotencyKey(),
			message.payload(), message.createdAt(), message.attempts() + 1, message.nextAttemptAt(),
			null, Instant.now(), message.deadLetteredAt()));
		return true;
	}

	@Override
	public synchronized boolean markFailed(String idempotencyKey, String error,
			Instant nextAttemptAt) {
		OutboxMessage message = messages.get(idempotencyKey);
		if (message == null) {
			return false;
		}
		messages.put(idempotencyKey, new OutboxMessage(message.topic(), message.idempotencyKey(),
			message.payload(), message.createdAt(), message.attempts() + 1, nextAttemptAt,
			error, message.publishedAt(), message.deadLetteredAt()));
		return true;
	}

	@Override
	public synchronized boolean markDeadLettered(String idempotencyKey, String error) {
		OutboxMessage message = messages.get(idempotencyKey);
		if (message == null) {
			return false;
		}
		messages.put(idempotencyKey, new OutboxMessage(message.topic(), message.idempotencyKey(),
			message.payload(), message.createdAt(), message.attempts() + 1, message.nextAttemptAt(),
			error, message.publishedAt(), Instant.now()));
		return true;
	}

	@Override
	public synchronized long pendingCount() {
		return messages.values().stream()
			.filter(message -> message.publishedAt() == null && message.deadLetteredAt() == null)
			.count();
	}

	@Override
	public synchronized long deadLetteredCount() {
		return messages.values().stream().filter(message -> message.deadLetteredAt() != null).count();
	}
}
