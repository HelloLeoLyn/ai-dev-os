package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import com.aidevos.orchestrator.execution.ExecutionAttempt;
import com.aidevos.orchestrator.execution.ExecutionAttemptRepository;
import com.aidevos.orchestrator.execution.ExecutionAttemptStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL implementation of the execution attempt repository backed by the
 * structured execution_attempts table.
 */
@Repository
@DependsOn("postgresDocumentStore")
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
public class PostgresExecutionAttemptRepository implements ExecutionAttemptRepository {

	private static final String COLUMNS = "id,job_id,attempt_no,execution_id,status,lease_owner,"
		+ "lease_token,lease_expires_at,heartbeat_at,failure_code,recovery_count,created_at,"
		+ "started_at,completed_at";

	private final DataSource dataSource;

	public PostgresExecutionAttemptRepository(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public void save(ExecutionAttempt attempt) {
		String sql = "INSERT INTO execution_attempts(" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
			+ "ON CONFLICT(id) DO UPDATE SET job_id=EXCLUDED.job_id,attempt_no=EXCLUDED.attempt_no,"
			+ "execution_id=EXCLUDED.execution_id,status=EXCLUDED.status,"
			+ "lease_owner=EXCLUDED.lease_owner,lease_token=EXCLUDED.lease_token,"
			+ "lease_expires_at=EXCLUDED.lease_expires_at,heartbeat_at=EXCLUDED.heartbeat_at,"
			+ "failure_code=EXCLUDED.failure_code,recovery_count=EXCLUDED.recovery_count,"
			+ "started_at=EXCLUDED.started_at,completed_at=EXCLUDED.completed_at";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, attempt.getId());
			statement.setString(2, attempt.getJobId());
			statement.setInt(3, attempt.getAttemptNo());
			statement.setString(4, attempt.getExecutionId());
			statement.setString(5, attempt.getStatus().name());
			statement.setString(6, attempt.getLeaseOwner());
			if (attempt.getLeaseToken() == null) {
				statement.setNull(7, java.sql.Types.BIGINT);
			} else {
				statement.setLong(7, attempt.getLeaseToken());
			}
			setTimestamp(statement, 8, attempt.getLeaseExpiresAt());
			setTimestamp(statement, 9, attempt.getHeartbeatAt());
			statement.setString(10, attempt.getFailureCode());
			statement.setInt(11, attempt.getRecoveryCount());
			statement.setTimestamp(12, Timestamp.from(attempt.getCreatedAt()));
			setTimestamp(statement, 13, attempt.getStartedAt());
			setTimestamp(statement, 14, attempt.getCompletedAt());
			statement.executeUpdate();
		}
		catch (SQLException exception) {
			throw failure("save attempt", exception);
		}
	}

	@Override
	public ExecutionAttempt get(String id) {
		String sql = "SELECT " + COLUMNS + " FROM execution_attempts WHERE id=?";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, id);
			try (ResultSet result = statement.executeQuery()) {
				return result.next() ? read(result) : null;
			}
		}
		catch (SQLException exception) {
			throw failure("read attempt", exception);
		}
	}

	@Override
	public List<ExecutionAttempt> getByJob(String jobId) {
		return select("SELECT " + COLUMNS + " FROM execution_attempts WHERE job_id=? "
			+ "ORDER BY attempt_no,id", jobId);
	}

	@Override
	public List<ExecutionAttempt> listActive() {
		return select("SELECT " + COLUMNS + " FROM execution_attempts "
			+ "WHERE status IN ('STARTING','RUNNING') ORDER BY created_at,id");
	}

	@Override
	public List<ExecutionAttempt> findAbandoned(Instant now) {
		return select("SELECT " + COLUMNS + " FROM execution_attempts WHERE status='RUNNING' "
			+ "AND lease_expires_at IS NOT NULL AND lease_expires_at<? ORDER BY created_at,id", now);
	}

	private List<ExecutionAttempt> select(String sql, Object... parameters) {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int i = 0; i < parameters.length; i++) {
				if (parameters[i] instanceof Instant instant) {
					statement.setTimestamp(i + 1, Timestamp.from(instant));
				} else {
					statement.setObject(i + 1, parameters[i]);
				}
			}
			List<ExecutionAttempt> attempts = new ArrayList<>();
			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					attempts.add(read(result));
				}
			}
			return attempts;
		}
		catch (SQLException exception) {
			throw failure("list attempts", exception);
		}
	}

	private ExecutionAttempt read(ResultSet result) throws SQLException {
		return ExecutionAttempt.restore(
			result.getString("id"),
			result.getString("job_id"),
			result.getInt("attempt_no"),
			result.getString("execution_id"),
			ExecutionAttemptStatus.valueOf(result.getString("status")),
			result.getString("lease_owner"),
			result.getObject("lease_token", Long.class),
			instant(result, "lease_expires_at"),
			instant(result, "heartbeat_at"),
			result.getString("failure_code"),
			result.getInt("recovery_count"),
			result.getTimestamp("created_at").toInstant(),
			instant(result, "started_at"),
			instant(result, "completed_at"));
	}

	private void setTimestamp(PreparedStatement statement, int index, Instant value)
			throws SQLException {
		if (value == null) {
			statement.setNull(index, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
		} else {
			statement.setTimestamp(index, Timestamp.from(value));
		}
	}

	private Instant instant(ResultSet result, String column) throws SQLException {
		Timestamp value = result.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	private IllegalStateException failure(String operation, Exception cause) {
		return new IllegalStateException("PostgreSQL attempt repository failed to " + operation, cause);
	}
}
