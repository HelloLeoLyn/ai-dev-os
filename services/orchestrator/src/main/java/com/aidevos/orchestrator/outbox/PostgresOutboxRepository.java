package com.aidevos.orchestrator.outbox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL outbox backed by the audit_outbox table. Claiming and publishing
 * happen on the same transaction-bound connection, so a crash between claim
 * and publish rolls back atomically and the row simply stays pending.
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
public class PostgresOutboxRepository implements OutboxRepository {

	private static final String COLUMNS = "idempotency_key,topic,event_payload::text AS payload,"
		+ "created_at,attempts,next_attempt_at,last_error,published_at,dead_lettered_at";

	private final DataSource dataSource;

	public PostgresOutboxRepository(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public OutboxMessage enqueue(String topic, String idempotencyKey, String payload) {
		String sql = "INSERT INTO audit_outbox(idempotency_key,topic,event_payload) "
			+ "VALUES (?,?,?::jsonb) ON CONFLICT(idempotency_key) DO NOTHING";
		Connection connection = JdbcConnectionContext.current(dataSource);
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, idempotencyKey);
			statement.setString(2, topic);
			statement.setString(3, payload);
			statement.executeUpdate();
		}
		catch (SQLException exception) {
			throw failure("enqueue outbox message", exception);
		}
		finally {
			JdbcConnectionContext.release(connection, dataSource);
		}
		OutboxMessage message = find(idempotencyKey);
		if (message == null) {
			throw new IllegalStateException("Outbox message was not found after enqueue: "
				+ idempotencyKey);
		}
		return message;
	}

	@Override
	public OutboxMessage find(String idempotencyKey) {
		Connection connection = JdbcConnectionContext.current(dataSource);
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT " + COLUMNS + " FROM audit_outbox WHERE idempotency_key=?")) {
			statement.setString(1, idempotencyKey);
			try (ResultSet result = statement.executeQuery()) {
				return result.next() ? read(result) : null;
			}
		}
		catch (SQLException exception) {
			throw failure("read outbox message", exception);
		}
		finally {
			JdbcConnectionContext.release(connection, dataSource);
		}
	}

	@Override
	public List<OutboxMessage> claimPending(Instant now, int limit) {
		String sql = "SELECT " + COLUMNS + " FROM audit_outbox "
			+ "WHERE published_at IS NULL AND dead_lettered_at IS NULL AND next_attempt_at<=? "
			+ "ORDER BY created_at,idempotency_key LIMIT ? FOR UPDATE SKIP LOCKED";
		Connection connection = JdbcConnectionContext.current(dataSource);
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setTimestamp(1, Timestamp.from(now));
			statement.setInt(2, limit);
			try (ResultSet result = statement.executeQuery()) {
				List<OutboxMessage> messages = new ArrayList<>();
				while (result.next()) {
					messages.add(read(result));
				}
				return List.copyOf(messages);
			}
		}
		catch (SQLException exception) {
			throw failure("claim outbox messages", exception);
		}
		finally {
			JdbcConnectionContext.release(connection, dataSource);
		}
	}

	@Override
	public boolean markPublished(String idempotencyKey) {
		return update("UPDATE audit_outbox SET published_at=CURRENT_TIMESTAMP,attempts=attempts+1,"
			+ "last_error=NULL WHERE idempotency_key=?", idempotencyKey);
	}

	@Override
	public boolean markFailed(String idempotencyKey, String error, Instant nextAttemptAt) {
		return update("UPDATE audit_outbox SET attempts=attempts+1,last_error=?,next_attempt_at=? "
			+ "WHERE idempotency_key=?", error, Timestamp.from(nextAttemptAt), idempotencyKey);
	}

	@Override
	public boolean markDeadLettered(String idempotencyKey, String error) {
		return update("UPDATE audit_outbox SET dead_lettered_at=CURRENT_TIMESTAMP,attempts=attempts+1,"
			+ "last_error=? WHERE idempotency_key=?", error, idempotencyKey);
	}

	@Override
	public long pendingCount() {
		return count("SELECT COUNT(*) FROM audit_outbox WHERE published_at IS NULL "
			+ "AND dead_lettered_at IS NULL");
	}

	@Override
	public long deadLetteredCount() {
		return count("SELECT COUNT(*) FROM audit_outbox WHERE dead_lettered_at IS NOT NULL");
	}

	private boolean update(String sql, Object... parameters) {
		Connection connection = JdbcConnectionContext.current(dataSource);
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int i = 0; i < parameters.length; i++) {
				statement.setObject(i + 1, parameters[i]);
			}
			return statement.executeUpdate() > 0;
		}
		catch (SQLException exception) {
			throw failure("update outbox message", exception);
		}
		finally {
			JdbcConnectionContext.release(connection, dataSource);
		}
	}

	private long count(String sql) {
		Connection connection = JdbcConnectionContext.current(dataSource);
		try (PreparedStatement statement = connection.prepareStatement(sql);
				ResultSet result = statement.executeQuery()) {
			result.next();
			return result.getLong(1);
		}
		catch (SQLException exception) {
			throw failure("count outbox messages", exception);
		}
		finally {
			JdbcConnectionContext.release(connection, dataSource);
		}
	}

	private OutboxMessage read(ResultSet result) throws SQLException {
		return new OutboxMessage(
			result.getString("topic"),
			result.getString("idempotency_key"),
			result.getString("payload"),
			result.getTimestamp("created_at").toInstant(),
			result.getInt("attempts"),
			result.getTimestamp("next_attempt_at").toInstant(),
			result.getString("last_error"),
			instant(result, "published_at"),
			instant(result, "dead_lettered_at"));
	}

	private Instant instant(ResultSet result, String column) throws SQLException {
		Timestamp value = result.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	private IllegalStateException failure(String operation, SQLException cause) {
		return new IllegalStateException("PostgreSQL outbox repository failed to " + operation, cause);
	}
}
