package com.aidevos.orchestrator.outbox;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL transaction boundary. The callback runs on a single JDBC
 * connection with auto-commit disabled; repositories inside it use
 * {@link JdbcConnectionContext#current} and therefore join the same
 * transaction. A nested call inside an active boundary joins it.
 */
@Component
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
public class PostgresOutboxTransactions implements OutboxTransactions {

	private final DataSource dataSource;

	public PostgresOutboxTransactions(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public <T> T execute(TransactionCallback<T> callback) {
		if (JdbcConnectionContext.active()) {
			return runUnchecked(callback);
		}
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			JdbcConnectionContext.bind(connection);
			try {
				T result = callback.run();
				connection.commit();
				return result;
			}
			catch (Exception exception) {
				JdbcConnectionContext.rollbackQuietly(connection);
				if (exception instanceof RuntimeException runtime) {
					throw runtime;
				}
				throw new IllegalStateException("Outbox transaction failed", exception);
			}
			finally {
				JdbcConnectionContext.unbind();
			}
		}
		catch (SQLException exception) {
			throw new IllegalStateException("Outbox transaction could not start", exception);
		}
	}

	private <T> T runUnchecked(TransactionCallback<T> callback) {
		try {
			return callback.run();
		}
		catch (Exception exception) {
			if (exception instanceof RuntimeException runtime) {
				throw runtime;
			}
			throw new IllegalStateException("Outbox transaction callback failed", exception);
		}
	}
}
