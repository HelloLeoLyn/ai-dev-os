package com.aidevos.orchestrator.audit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;

import com.aidevos.orchestrator.outbox.AuditOutboxConsumer;
import com.aidevos.orchestrator.outbox.JdbcConnectionContext;
import com.aidevos.orchestrator.outbox.OutboxMessage;
import com.aidevos.orchestrator.outbox.OutboxRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/**
 * PostgreSQL audit repository. Appends become durable outbox entries; inside
 * an active business transaction the entry joins that transaction and the
 * background relay publishes it after commit. Standalone appends publish
 * inline so the existing immediate-visibility contract is preserved. Queries
 * are read-only and never trigger publishing.
 */
@Repository
@DependsOn("postgresDocumentStore")
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
public class PostgresAuditRepository implements AuditRepository {
	private static final String COLUMNS = "sequence_id,payload::text";
	private final DataSource dataSource;
	private final ObjectMapper mapper;
	private final OutboxRepository outboxRepository;
	private final AuditOutboxConsumer auditConsumer;

	public PostgresAuditRepository(DataSource dataSource, ObjectMapper mapper,
			OutboxRepository outboxRepository, AuditOutboxConsumer auditConsumer) {
		this.dataSource = dataSource;
		this.mapper = mapper;
		this.outboxRepository = outboxRepository;
		this.auditConsumer = auditConsumer;
	}

	@Override
	public EventRecord append(EventRecord event) {
		checkIdNotReused(event);
		if (JdbcConnectionContext.active()) {
			outboxRepository.enqueue(AuditOutboxConsumer.TOPIC, event.idempotencyKey(),
				payload(event));
			return event;
		}
		outboxRepository.enqueue(AuditOutboxConsumer.TOPIC, event.idempotencyKey(),
			payload(event));
		publishInline(event.idempotencyKey());
		return getByIdempotencyKey(event.idempotencyKey());
	}

	/**
	 * Enqueue is durable before inline publishing; a publication failure leaves
	 * the pending row for the relay instead of losing the event.
	 */
	private void publishInline(String idempotencyKey) {
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			JdbcConnectionContext.bind(connection);
			try {
				OutboxMessage message = outboxRepository.find(idempotencyKey);
				if (message != null) {
					auditConsumer.consume(message);
					outboxRepository.markPublished(idempotencyKey);
				}
				connection.commit();
			}
			catch (Exception exception) {
				JdbcConnectionContext.rollbackQuietly(connection);
				throw failure("publish outbox", exception);
			}
			finally {
				JdbcConnectionContext.unbind();
			}
		}
		catch (SQLException exception) {
			throw failure("publish outbox", exception);
		}
	}

	private void checkIdNotReused(EventRecord event) {
		Connection connection = JdbcConnectionContext.current(dataSource);
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT idempotency_key FROM audit_events WHERE id=? "
					+ "UNION ALL SELECT idempotency_key FROM audit_outbox "
					+ "WHERE event_payload->>'id'=?")) {
			statement.setString(1, event.id());
			statement.setString(2, event.id());
			try (ResultSet result = statement.executeQuery()) {
				if (result.next() && !event.idempotencyKey().equals(result.getString(1))) {
					throw new IllegalStateException("Audit event id already exists: " + event.id());
				}
			}
		}
		catch (SQLException exception) {
			throw failure("check event id", exception);
		}
		finally {
			JdbcConnectionContext.release(connection, dataSource);
		}
	}

	private String payload(EventRecord event) {
		try {
			return mapper.writeValueAsString(event);
		}
		catch (Exception exception) {
			throw new IllegalStateException("Failed to serialize audit event", exception);
		}
	}

	private EventRecord getByIdempotencyKey(String key) {
		try (Connection connection = dataSource.getConnection(); PreparedStatement statement =
				connection.prepareStatement("SELECT " + COLUMNS
					+ " FROM audit_events WHERE idempotency_key=?")) {
			statement.setString(1, key);
			try (ResultSet result = statement.executeQuery()) {
				if (result.next()) {
					return read(result);
				}
				throw new IllegalStateException("Idempotent audit event was not found");
			}
		}
		catch (Exception exception) { throw failure("read published event", exception); }
	}

	@Override
	public EventRecord get(String id) {
		try (Connection connection = dataSource.getConnection(); PreparedStatement statement =
				connection.prepareStatement("SELECT " + COLUMNS + " FROM audit_events WHERE id=?")) {
			statement.setString(1, id);
			try (ResultSet result = statement.executeQuery()) {
				return result.next() ? read(result) : null;
			}
		}
		catch (Exception exception) { throw failure("read", exception); }
	}

	@Override
	public List<EventRecord> query(EventQuery query) {
		EventQuery effective = query == null ? EventQuery.all() : query;
		SqlFilter filter = filter(effective);
		List<EventRecord> matches = new ArrayList<>();
		String sql = "SELECT " + COLUMNS + " FROM audit_events" + filter.where()
			+ " ORDER BY occurred_at,sequence_id,id OFFSET ? LIMIT ?";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			int index = bindFilter(statement, filter.values());
			statement.setInt(index++, effective.offset());
			statement.setInt(index, effective.limit());
			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					matches.add(read(result));
				}
			}
			return List.copyOf(matches);
		}
		catch (Exception exception) { throw failure("query", exception); }
	}

	@Override
	public long count(EventQuery query) {
		EventQuery effective = query == null ? EventQuery.all() : query;
		SqlFilter filter = filter(effective);
		try (Connection connection = dataSource.getConnection(); PreparedStatement statement =
				connection.prepareStatement("SELECT COUNT(*) FROM audit_events" + filter.where())) {
			bindFilter(statement, filter.values());
			try (ResultSet result = statement.executeQuery()) {
				result.next();
				return result.getLong(1);
			}
		}
		catch (Exception exception) { throw failure("count", exception); }
	}

	private SqlFilter filter(EventQuery query) {
		List<String> clauses = new ArrayList<>();
		List<Object> values = new ArrayList<>();
		add(clauses, values, "aggregate_type", query.aggregateType());
		add(clauses, values, "aggregate_id", query.aggregateId());
		add(clauses, values, "plan_run_id", query.planRunId());
		add(clauses, values, "step_run_id", query.stepRunId());
		add(clauses, values, "attempt_id", query.attemptId());
		add(clauses, values, "job_id", query.jobId());
		add(clauses, values, "execution_id", query.executionId());
		add(clauses, values, "execution_record_id", query.executionRecordId());
		add(clauses, values, "invocation_id", query.invocationId());
		add(clauses, values, "approval_id", query.approvalId());
		if (!query.eventTypes().isEmpty()) {
			clauses.add("event_type IN (" + String.join(",",
				java.util.Collections.nCopies(query.eventTypes().size(), "?")) + ")");
			query.eventTypes().stream().map(Enum::name).sorted().forEach(values::add);
		}
		if (query.occurredAfter() != null) {
			clauses.add("occurred_at>=?"); values.add(Timestamp.from(query.occurredAfter()));
		}
		if (query.occurredBefore() != null) {
			clauses.add("occurred_at<=?"); values.add(Timestamp.from(query.occurredBefore()));
		}
		return new SqlFilter(clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses),
			values);
	}

	private void add(List<String> clauses, List<Object> values, String column, String value) {
		if (value != null) { clauses.add(column + "=?"); values.add(value); }
	}

	private int bindFilter(PreparedStatement statement, List<Object> values) throws SQLException {
		int index = 1;
		for (Object value : values) statement.setObject(index++, value);
		return index;
	}

	private record SqlFilter(String where, List<Object> values) { }

	private EventRecord read(ResultSet result) throws Exception {
		return mapper.readValue(result.getString(2), EventRecord.class).withSequence(result.getLong(1));
	}

	private IllegalStateException failure(String operation, Exception cause) {
		return new IllegalStateException("PostgreSQL audit repository failed to " + operation, cause);
	}
}
