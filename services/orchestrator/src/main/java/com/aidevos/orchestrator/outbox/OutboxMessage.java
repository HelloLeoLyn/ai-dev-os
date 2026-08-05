package com.aidevos.orchestrator.outbox;

import java.time.Instant;

/**
 * A durable outbox entry. The idempotency key is the primary key; consumers
 * must treat payload delivery as at-least-once and deduplicate by key.
 */
public record OutboxMessage(
		String topic,
		String idempotencyKey,
		String payload,
		Instant createdAt,
		int attempts,
		Instant nextAttemptAt,
		String lastError,
		Instant publishedAt,
		Instant deadLetteredAt) {

	public OutboxMessage {
		topic = requireText(topic, "Outbox topic is required");
		idempotencyKey = requireText(idempotencyKey, "Outbox idempotency key is required");
		payload = requireText(payload, "Outbox payload is required");
		if (createdAt == null) throw new IllegalArgumentException("Outbox created time is required");
		if (nextAttemptAt == null) throw new IllegalArgumentException("Outbox next attempt time is required");
	}

	public boolean pending(Instant now) {
		return publishedAt == null && deadLetteredAt == null && !nextAttemptAt.isAfter(now);
	}

	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
		return value;
	}
}
