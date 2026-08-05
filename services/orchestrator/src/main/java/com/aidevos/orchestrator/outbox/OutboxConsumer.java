package com.aidevos.orchestrator.outbox;

/**
 * In-process consumer for a single outbox topic. The relay claims a pending
 * message and invokes the matching consumer inside the same JDBC transaction
 * as {@code markPublished}; a thrown exception schedules a backoff retry.
 */
public interface OutboxConsumer {

	String topic();

	void consume(OutboxMessage message);
}
