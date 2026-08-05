package com.aidevos.orchestrator.outbox;

import java.time.Instant;
import java.util.List;

/**
 * Transactional outbox repository. Enqueue must participate in the active
 * JDBC transaction (if any) so business state and outbox rows commit or roll
 * back together.
 */
public interface OutboxRepository {

	OutboxMessage enqueue(String topic, String idempotencyKey, String payload);

	OutboxMessage find(String idempotencyKey);

	List<OutboxMessage> claimPending(Instant now, int limit);

	boolean markPublished(String idempotencyKey);

	boolean markFailed(String idempotencyKey, String error, Instant nextAttemptAt);

	boolean markDeadLettered(String idempotencyKey, String error);

	long pendingCount();

	long deadLetteredCount();
}
