package com.aidevos.orchestrator.audit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
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
		String sql = "INSERT INTO audit_events(id,event_type,occurred_at,aggregate_type,aggregate_id,"
			+ "plan_run_id,step_run_id,attempt_id,job_id,execution_id,execution_record_id,"
			+ "invocation_id,approval_id,idempotency_key,payload) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb) "
			+ "ON CONFLICT(idempotency_key) DO NOTHING RETURNING sequence_id";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			bind(statement, event);
			try (ResultSet result = statement.executeQuery()) {
				if (result.next()) return event.withSequence(result.getLong(1));
			}
			return getByIdempotencyKey(connection, event.idempotencyKey());
		}
		catch (Exception exception) { throw failure("append", exception); }
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
		List<EventRecord> matches = new ArrayList<>();
		String sql = "SELECT " + COLUMNS + " FROM audit_events ORDER BY occurred_at,sequence_id,id";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql);
				ResultSet result = statement.executeQuery()) {
			while (result.next()) {
				EventRecord event = read(result);
				if (effective.matches(event)) matches.add(event);
			}
			return matches.stream().skip(effective.offset()).limit(effective.limit()).toList();
		}
		catch (Exception exception) { throw failure("query", exception); }
	}

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
