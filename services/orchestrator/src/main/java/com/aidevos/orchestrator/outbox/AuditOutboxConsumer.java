package com.aidevos.orchestrator.outbox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import javax.sql.DataSource;
import com.aidevos.orchestrator.audit.EventRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Materializes audit outbox entries into audit_events. Runs inside the relay's
 * transaction, so the event insert and the published marker commit atomically;
 * the idempotency key keeps repeated deliveries harmless.
 */
@Component
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
public class AuditOutboxConsumer implements OutboxConsumer {

	public static final String TOPIC = "audit";
	private static final String COLUMNS = "sequence_id,payload::text";

	private final DataSource dataSource;
	private final ObjectMapper mapper;

	public AuditOutboxConsumer(DataSource dataSource, ObjectMapper mapper) {
		this.dataSource = dataSource;
		this.mapper = mapper;
	}

	@Override
	public String topic() {
		return TOPIC;
	}

	@Override
	public void consume(OutboxMessage message) {
		EventRecord event;
		try {
			event = mapper.readValue(message.payload(), EventRecord.class);
		}
		catch (Exception exception) {
			throw new IllegalStateException("Invalid audit outbox payload for key "
				+ message.idempotencyKey(), exception);
		}
		Connection connection = JdbcConnectionContext.current(dataSource);
		try {
			insertEvent(connection, event);
		}
		catch (Exception exception) {
			throw new IllegalStateException("Audit outbox consumer failed to publish "
				+ message.idempotencyKey(), exception);
		}
		finally {
			JdbcConnectionContext.release(connection, dataSource);
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
				if (result.next()) {
					return;
				}
			}
			getByIdempotencyKey(connection, event.idempotencyKey());
		}
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
				if (result.next()) {
					return read(result);
				}
				throw new IllegalStateException("Idempotent audit event was not found");
			}
		}
	}

	private EventRecord read(ResultSet result) throws Exception {
		return mapper.readValue(result.getString(2), EventRecord.class).withSequence(result.getLong(1));
	}
}
