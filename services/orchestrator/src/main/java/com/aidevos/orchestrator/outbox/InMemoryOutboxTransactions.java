package com.aidevos.orchestrator.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-memory pass-through boundary: callbacks run as before and no JDBC
 * transaction is opened, keeping the in-memory persistence mode unchanged.
 */
@Component
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory",
	matchIfMissing = true)
public class InMemoryOutboxTransactions implements OutboxTransactions {

	@Override
	public <T> T execute(TransactionCallback<T> callback) {
		return OutboxTransactions.passThrough().execute(callback);
	}
}
