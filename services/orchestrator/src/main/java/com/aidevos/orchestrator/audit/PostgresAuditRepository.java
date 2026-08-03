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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
@DependsOn("postgresDocumentStore")
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
public class PostgresAuditRepository implements AuditRepository {
	private static final String COLUMNS = "sequence_id,payload::text";
	private final DataSource dataSource;
	private final ObjectMapper mapper;

	public PostgresAuditRepository(DataSource dataSource, ObjectMapper mapper) {
		this.dataSource = dataSource;
		this.mapper = mapper;
	}

	@Override
	public EventRecord append(EventRecord event) {
		persistOutbox(event);
		drainOutbox();
		try (Connection connection = dataSource.getConnection()) {
			return getByIdempotencyKey(connection, event.idempotencyKey());
		}
		catch (Exception exception) { throw failure("read published event", exception); }
	}

	private void persistOutbox(EventRecord event) {
		String sql = "INSERT INTO audit_outbox(idempotency_key,event_payload) VALUES (?,?::jsonb) "
			+ "ON CONFLICT(idempotency_key) DO NOTHING";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			try (PreparedStatement existing = connection.prepareStatement(
					"SELECT idempotency_key FROM audit_events WHERE id=?")) {
				existing.setString(1, event.id());
				try (ResultSet result = existing.executeQuery()) {
					if (result.next() && !event.idempotencyKey().equals(result.getString(1))) {
						throw new IllegalStateException("Audit event id already exists: " + event.id());
					}
				}
			}
			statement.setString(1, event.idempotencyKey());
			statement.setString(2, mapper.writeValueAsString(event));
			statement.executeUpdate();
		}
		catch (Exception exception) { throw failure("enqueue outbox", exception); }
	}

	private void drainOutbox() {
		String select = "SELECT idempotency_key,event_payload::text FROM audit_outbox "
			+ "WHERE published_at IS NULL ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED";
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (PreparedStatement pending = connection.prepareStatement(select);
					ResultSet rows = pending.executeQuery()) {
				while (rows.next()) {
					EventRecord event = mapper.readValue(rows.getString(2), EventRecord.class);
					insertEvent(connection, event);
					try (PreparedStatement published = connection.prepareStatement(
							"UPDATE audit_outbox SET published_at=CURRENT_TIMESTAMP,attempts=attempts+1,last_error=NULL WHERE idempotency_key=?")) {
						published.setString(1, rows.getString(1));
						published.executeUpdate();
					}
				}
			}
			connection.commit();
		}
		catch (Exception exception) {
			recordOutboxFailure(exception);
			throw failure("publish outbox", exception);
		}
	}

	private void recordOutboxFailure(Exception failure) {
		try (Connection connection = dataSource.getConnection(); PreparedStatement statement =
				connection.prepareStatement("UPDATE audit_outbox SET attempts=attempts+1,last_error=? WHERE published_at IS NULL")) {
			statement.setString(1, failure.getMessage());
			statement.executeUpdate();
		}
		catch (SQLException ignored) {
			// The original publication failure remains the actionable error.
		}
	}

	private void insertEvent(Connection connection, EventRecord event) throws Exception {
		String sql = "INSERT INTO audit_events(id,event_type,occurred_at,aggregate_type,aggregate_id,"
			+ "plan_run_id,step_run_id,attempt_id,job_id,execution_id,execution_record_id,"
			+ "invocation_id,approval_id,idempotency_key,payload) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb) "
			+ "ON CONFLICT(idempotency_key) DO NOTHING RETURNING sequence_id";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			bind(statement, event);
			try (ResultSet result = statement.executeQuery()) {
				if (result.next()) return;
			}
			getByIdempotencyKey(connection, event.idempotencyKey());
		}
	}

	@Override
	public EventRecord get(String id) {
		drainOutbox();
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
		drainOutbox();
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
		drainOutbox();
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

	private void bind(PreparedStatement statement, EventRecord event) throws Exception {
		statement.setString(1, event.id()); statement.setString(2, event.type().name());
		statement.setTimestamp(3, Timestamp.from(event.occurredAt()));
		statement.setString(4, event.aggregateType()); statement.setString(5, event.aggregateId());
		statement.setString(6, event.planRunId()); statement.setString(7, event.stepRunId());
		statement.setString(8, event.attemptId()); statement.setString(9, event.jobId());
		statement.setString(10, event.executionId()); statement.setString(11, event.executionRecordId());
		statement.setString(12, event.invocationId()); statement.setString(13, event.approvalId());
		statement.setString(14, event.idempotencyKey());
		statement.setString(15, mapper.writeValueAsString(event));
	}

	private EventRecord getByIdempotencyKey(Connection connection, String key) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT " + COLUMNS + " FROM audit_events WHERE idempotency_key=?")) {
			statement.setString(1, key);
			try (ResultSet result = statement.executeQuery()) {
				if (result.next()) return read(result);
				throw new IllegalStateException("Idempotent audit event was not found");
			}
		}
	}

	private EventRecord read(ResultSet result) throws Exception {
		return mapper.readValue(result.getString(2), EventRecord.class).withSequence(result.getLong(1));
	}

	private IllegalStateException failure(String operation, Exception cause) {
		return new IllegalStateException("PostgreSQL audit repository failed to " + operation, cause);
	}
}
