package com.aidevos.orchestrator.outbox;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * Thread-local JDBC connection binding used by the transactional outbox
 * boundary. Repositories ask for the current connection so that every write
 * inside an active transaction joins it instead of opening a private one.
 *
 * <p>Standalone acquisitions (no active transaction) fail fast when the
 * calling thread is interrupted: a thread being shut down must not open a new
 * connection to a database that is going away, for example the outbox relay
 * scheduler stopping after its Testcontainers PostgreSQL was torn down.
 * Transaction-bound acquisitions are unaffected because an active transaction
 * owns its connection and must be allowed to finish or roll back.
 */
public final class JdbcConnectionContext {

	private static final ThreadLocal<Connection> BOUND = new ThreadLocal<>();

	private JdbcConnectionContext() { }

	public static boolean active() {
		return BOUND.get() != null;
	}

	public static void bind(Connection connection) {
		BOUND.set(connection);
	}

	public static void unbind() {
		BOUND.remove();
	}

	/**
	 * Returns the connection bound to the current thread, or opens one from
	 * the data source. Fails fast with {@link IllegalStateException} when no
	 * transaction is active and the calling thread is interrupted.
	 */
	public static Connection current(DataSource dataSource) {
		Connection bound = BOUND.get();
		if (bound != null) {
			return bound;
		}
		if (Thread.currentThread().isInterrupted()) {
			throw new IllegalStateException(
				"JDBC connection acquisition aborted: calling thread is interrupted");
		}
		try {
			return dataSource.getConnection();
		}
		catch (SQLException exception) {
			throw new IllegalStateException("Failed to open JDBC connection", exception);
		}
	}

	/**
	 * Closes a connection only when it was opened by the caller. A
	 * transaction-bound connection is owned by the transaction and is closed
	 * by the boundary that bound it.
	 */
	public static void release(Connection connection, DataSource dataSource) {
		if (connection == null || BOUND.get() == connection) {
			return;
		}
		try {
			connection.close();
		}
		catch (SQLException ignored) {
			// Closing a standalone connection must not mask the operation result.
		}
	}

	public static void rollbackQuietly(Connection connection) {
		try {
			connection.rollback();
		}
		catch (SQLException ignored) {
			// The originating failure remains the actionable error.
		}
	}
}
