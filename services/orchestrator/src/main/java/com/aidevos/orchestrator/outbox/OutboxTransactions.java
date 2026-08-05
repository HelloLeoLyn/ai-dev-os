package com.aidevos.orchestrator.outbox;

/**
 * Transaction boundary used by business write flows. In PostgreSQL mode the
 * callback and every repository call inside it share one JDBC transaction, so
 * aggregate updates and outbox enqueues commit or roll back together. The
 * in-memory mode is a pass-through and keeps existing behavior unchanged.
 */
public interface OutboxTransactions {

	@FunctionalInterface
	interface TransactionCallback<T> {
		T run() throws Exception;
	}

	<T> T execute(TransactionCallback<T> callback);

	static OutboxTransactions passThrough() {
		return new OutboxTransactions() {
			@Override
			public <T> T execute(TransactionCallback<T> callback) {
				try {
					return callback.run();
				}
				catch (RuntimeException exception) {
					throw exception;
				}
				catch (Exception exception) {
					throw new IllegalStateException("Outbox transaction callback failed", exception);
				}
			}
		};
	}
}
