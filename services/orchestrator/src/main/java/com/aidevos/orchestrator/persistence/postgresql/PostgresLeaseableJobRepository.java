package com.aidevos.orchestrator.persistence.postgresql;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobLease;
import com.aidevos.orchestrator.job.JobStatus;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.persistence.LeaseableJobRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/**
 * PostgreSQL implementation of the leaseable job repository backed by the
 * structured jobs table. Lease-bound writes are single SQL statements whose
 * affected-row count doubles as the fencing check.
 */
@Repository
@DependsOn("postgresDocumentStore")
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
public class PostgresLeaseableJobRepository implements LeaseableJobRepository {

	private static final String JOB_COLUMNS = "id,task_snapshot,created_at,status,started_at,"
		+ "completed_at,result,execution_record_id,result_summary,error_message,approval_id,"
		+ "attempt_no,max_attempts,available_at,priority,lease_owner,lease_token,lease_expires_at,"
		+ "heartbeat_at,version,recovery_count,last_failure_code,recovery_policy";

	private final DataSource dataSource;
	private final ObjectMapper mapper;

	public PostgresLeaseableJobRepository(DataSource dataSource, ObjectMapper mapper) {
		this.dataSource = dataSource;
		this.mapper = mapper;
	}

	@Override
	public void save(ExecutionJob job) {
		String sql = "INSERT INTO jobs(" + JOB_COLUMNS + ") VALUES (?,?::jsonb,?,?,?,?,?::jsonb,"
			+ "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET "
			+ "task_snapshot=EXCLUDED.task_snapshot,status=EXCLUDED.status,"
			+ "started_at=EXCLUDED.started_at,completed_at=EXCLUDED.completed_at,"
			+ "result=EXCLUDED.result,execution_record_id=EXCLUDED.execution_record_id,"
			+ "result_summary=EXCLUDED.result_summary,error_message=EXCLUDED.error_message,"
			+ "approval_id=EXCLUDED.approval_id,attempt_no=EXCLUDED.attempt_no,"
			+ "max_attempts=EXCLUDED.max_attempts,available_at=EXCLUDED.available_at,"
			+ "priority=EXCLUDED.priority,lease_owner=EXCLUDED.lease_owner,"
			+ "lease_token=EXCLUDED.lease_token,lease_expires_at=EXCLUDED.lease_expires_at,"
			+ "heartbeat_at=EXCLUDED.heartbeat_at,version=EXCLUDED.version,"
			+ "recovery_count=EXCLUDED.recovery_count,"
			+ "last_failure_code=EXCLUDED.last_failure_code,recovery_policy=EXCLUDED.recovery_policy";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			bind(statement, job);
			statement.executeUpdate();
		}
		catch (Exception exception) {
			throw failure("save job", exception);
		}
	}

	@Override
	public ExecutionJob createIfAbsent(ExecutionJob job) {
		String sql = "INSERT INTO jobs(" + JOB_COLUMNS + ") VALUES (?,?::jsonb,?,?,?,?,?::jsonb,"
			+ "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO NOTHING";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			bind(statement, job);
			return statement.executeUpdate() == 1 ? job : get(job.getId());
		}
		catch (Exception exception) {
			throw failure("create job idempotently", exception);
		}
	}

	private void bind(PreparedStatement statement, ExecutionJob job) throws Exception {
		statement.setString(1, job.getId());
		statement.setString(2, mapper.writeValueAsString(job.getTaskSnapshot()));
		statement.setTimestamp(3, Timestamp.from(job.getCreatedAt()));
		statement.setString(4, job.getStatus().name());
		setTimestamp(statement, 5, job.getStartedAt());
		setTimestamp(statement, 6, job.getCompletedAt());
		setJson(statement, 7, job.getResult());
		statement.setString(8, job.getExecutionRecordId());
		statement.setString(9, job.getResultSummary());
		statement.setString(10, job.getErrorMessage());
		statement.setString(11, job.getApprovalId());
		statement.setInt(12, job.getAttemptNo());
		statement.setInt(13, job.getMaxAttempts());
		setTimestamp(statement, 14, job.getAvailableAt());
		statement.setInt(15, job.getPriority());
		statement.setString(16, job.getLeaseOwner());
		if (job.getLeaseToken() == null) {
			statement.setNull(17, java.sql.Types.BIGINT);
		} else {
			statement.setLong(17, job.getLeaseToken());
		}
		setTimestamp(statement, 18, job.getLeaseExpiresAt());
		setTimestamp(statement, 19, job.getHeartbeatAt());
		statement.setInt(20, job.getVersion());
		statement.setInt(21, job.getRecoveryCount());
		statement.setString(22, job.getLastFailureCode());
		statement.setString(23, job.getRecoveryPolicy().name());
	}

	@Override
	public ExecutionJob get(String id) {
		String sql = "SELECT " + JOB_COLUMNS + " FROM jobs WHERE id=?";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, id);
			try (ResultSet result = statement.executeQuery()) {
				return result.next() ? read(result) : null;
			}
		}
		catch (Exception exception) {
			throw failure("read job", exception);
		}
	}

	@Override
	public List<ExecutionJob> getAll() {
		return select("SELECT " + JOB_COLUMNS + " FROM jobs ORDER BY created_at,id");
	}

	@Override
	public List<ExecutionJob> getByStatus(JobStatus status) {
		return select("SELECT " + JOB_COLUMNS + " FROM jobs WHERE status=? ORDER BY created_at,id",
			status.name());
	}

	@Override
	public void remove(String id) {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM jobs WHERE id=?")) {
			statement.setString(1, id);
			statement.executeUpdate();
		}
		catch (SQLException exception) {
			throw failure("remove job", exception);
		}
	}

	@Override
	public Optional<JobLease> claimNext(Instant now, String owner, Duration leaseDuration) {
		String sql = """
			WITH candidate AS (
				SELECT id FROM jobs
				WHERE status IN ('QUEUED','RETRY_WAIT')
				  AND (available_at IS NULL OR available_at <= ?)
				ORDER BY created_at, id
				LIMIT 1
				FOR UPDATE SKIP LOCKED
			)
			UPDATE jobs SET
				status='RUNNING',
				started_at=?,
				attempt_no=attempt_no+1,
				version=version+1,
				lease_owner=?,
				lease_token=COALESCE(lease_token,0)+1,
				lease_expires_at=?,
				heartbeat_at=?
			WHERE id IN (SELECT id FROM candidate)
			RETURNING lease_token, lease_expires_at
			""";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setTimestamp(1, Timestamp.from(now));
			statement.setTimestamp(2, Timestamp.from(now));
			statement.setString(3, owner);
			statement.setTimestamp(4, Timestamp.from(now.plus(leaseDuration)));
			statement.setTimestamp(5, Timestamp.from(now));
			try (ResultSet result = statement.executeQuery()) {
				if (!result.next()) {
					return Optional.empty();
				}
				long token = result.getLong("lease_token");
				Instant expiresAt = result.getTimestamp("lease_expires_at").toInstant();
				return Optional.of(new JobLease(owner, token, expiresAt));
			}
		}
		catch (SQLException exception) {
			throw failure("claim job", exception);
		}
	}

	@Override
	public boolean renewLease(String jobId, String owner, long token, Instant newExpiry) {
		String sql = "UPDATE jobs SET lease_expires_at=?,heartbeat_at=?,version=version+1 "
			+ "WHERE id=? AND status='RUNNING' AND lease_owner=? AND lease_token=?";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setTimestamp(1, Timestamp.from(newExpiry));
			statement.setTimestamp(2, Timestamp.from(Instant.now()));
			statement.setString(3, jobId);
			statement.setString(4, owner);
			statement.setLong(5, token);
			return statement.executeUpdate() > 0;
		}
		catch (SQLException exception) {
			throw failure("renew lease", exception);
		}
	}

	@Override
	public boolean releaseLease(String jobId, String owner, long token, JobStatus nextStatus) {
		String sql;
		if (nextStatus == JobStatus.QUEUED) {
			sql = "UPDATE jobs SET status='QUEUED',lease_owner=NULL,lease_expires_at=NULL,"
				+ "heartbeat_at=NULL,version=version+1 "
				+ "WHERE id=? AND status='RUNNING' AND lease_owner=? AND lease_token=?";
		} else if (nextStatus == JobStatus.CANCELLED) {
			sql = "UPDATE jobs SET status='CANCELLED',completed_at=?,lease_owner=NULL,"
				+ "lease_expires_at=NULL,heartbeat_at=NULL,version=version+1 "
				+ "WHERE id=? AND status='RUNNING' AND lease_owner=? AND lease_token=?";
		} else {
			return false;
		}
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			int index = 1;
			if (nextStatus == JobStatus.CANCELLED) {
				statement.setTimestamp(index++, Timestamp.from(Instant.now()));
			}
			statement.setString(index++, jobId);
			statement.setString(index++, owner);
			statement.setLong(index, token);
			return statement.executeUpdate() > 0;
		}
		catch (SQLException exception) {
			throw failure("release lease", exception);
		}
	}

	@Override
	public boolean complete(String jobId, String owner, long token, ExecutionJob finalSnapshot) {
		JobStatus finalStatus = finalSnapshot.getStatus();
		if (finalStatus != JobStatus.SUCCESS && finalStatus != JobStatus.FAILED
				&& finalStatus != JobStatus.WAITING_APPROVAL) {
			return false;
		}
		String sql = """
			UPDATE jobs SET
				status=?, started_at=?, completed_at=?, result=?::jsonb,
				execution_record_id=?, result_summary=?, error_message=?, approval_id=?,
				attempt_no=?, max_attempts=?, available_at=?, priority=?,
				last_failure_code=?, recovery_policy=?,
				lease_owner=NULL, lease_expires_at=NULL, heartbeat_at=NULL,
				version=version+1
			WHERE id=? AND lease_owner=? AND lease_token=?
			""";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, finalStatus.name());
			setTimestamp(statement, 2, finalSnapshot.getStartedAt());
			setTimestamp(statement, 3, finalSnapshot.getCompletedAt());
			setJson(statement, 4, finalSnapshot.getResult());
			statement.setString(5, finalSnapshot.getExecutionRecordId());
			statement.setString(6, finalSnapshot.getResultSummary());
			statement.setString(7, finalSnapshot.getErrorMessage());
			statement.setString(8, finalSnapshot.getApprovalId());
			statement.setInt(9, finalSnapshot.getAttemptNo());
			statement.setInt(10, finalSnapshot.getMaxAttempts());
			setTimestamp(statement, 11, finalSnapshot.getAvailableAt());
			statement.setInt(12, finalSnapshot.getPriority());
			statement.setString(13, finalSnapshot.getLastFailureCode());
			statement.setString(14, finalSnapshot.getRecoveryPolicy().name());
			statement.setString(15, jobId);
			statement.setString(16, owner);
			statement.setLong(17, token);
			return statement.executeUpdate() > 0;
		}
		catch (Exception exception) {
			throw failure("complete job", exception);
		}
	}

	@Override
	public List<ExecutionJob> findStale(Instant now, int limit) {
		return select("SELECT " + JOB_COLUMNS + " FROM jobs WHERE status='RUNNING' "
			+ "AND lease_expires_at IS NOT NULL AND lease_expires_at<? "
			+ "ORDER BY lease_expires_at,id LIMIT ?", now, limit);
	}

	@Override
	public boolean markRecoveryRequired(String jobId, String failureCode) {
		String sql = "UPDATE jobs SET status='RECOVERY_REQUIRED',last_failure_code=?,"
			+ "recovery_count=recovery_count+1,lease_owner=NULL,lease_expires_at=NULL,"
			+ "heartbeat_at=NULL,version=version+1 WHERE id=? AND status='RUNNING'";
		return update(sql, failureCode, jobId);
	}

	@Override
	public boolean retryWait(String jobId, String failureCode, Instant availableAt) {
		String sql = "UPDATE jobs SET status='RETRY_WAIT',last_failure_code=?,available_at=?,"
			+ "lease_owner=NULL,lease_expires_at=NULL,heartbeat_at=NULL,version=version+1 "
			+ "WHERE id=? AND status='RUNNING'";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, failureCode);
			statement.setTimestamp(2, Timestamp.from(availableAt));
			statement.setString(3, jobId);
			return statement.executeUpdate() > 0;
		}
		catch (SQLException exception) {
			throw failure("retry wait", exception);
		}
	}

	@Override
	public boolean cancel(String jobId) {
		String sql = "UPDATE jobs SET status='CANCELLED',completed_at=?,lease_owner=NULL,"
			+ "lease_expires_at=NULL,heartbeat_at=NULL,version=version+1 "
			+ "WHERE id=? AND status NOT IN ('SUCCESS','FAILED')";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setTimestamp(1, Timestamp.from(Instant.now()));
			statement.setString(2, jobId);
			return statement.executeUpdate() > 0;
		}
		catch (SQLException exception) {
			throw failure("cancel job", exception);
		}
	}

	private boolean update(String sql, Object... parameters) {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int i = 0; i < parameters.length; i++) {
				statement.setObject(i + 1, parameters[i]);
			}
			return statement.executeUpdate() > 0;
		}
		catch (SQLException exception) {
			throw failure("update job", exception);
		}
	}

	private List<ExecutionJob> select(String sql, Object... parameters) {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int i = 0; i < parameters.length; i++) {
				if (parameters[i] instanceof Instant instant) {
					statement.setTimestamp(i + 1, Timestamp.from(instant));
				} else {
					statement.setObject(i + 1, parameters[i]);
				}
			}
			List<ExecutionJob> jobs = new ArrayList<>();
			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					jobs.add(read(result));
				}
			}
			return jobs;
		}
		catch (Exception exception) {
			throw failure("list jobs", exception);
		}
	}

	private ExecutionJob read(ResultSet result) throws SQLException, IOException {
		PersistenceSnapshots.Job snapshot = new PersistenceSnapshots.Job(
			result.getString("id"),
			mapper.readValue(result.getString("task_snapshot"), TaskDefinition.class),
			result.getTimestamp("created_at").toInstant(),
			JobStatus.valueOf(result.getString("status")),
			instant(result, "started_at"),
			instant(result, "completed_at"),
			result.getString("result") == null
				? null : mapper.readValue(result.getString("result"), ExecutionResult.class),
			result.getString("execution_record_id"),
			result.getString("result_summary"),
			result.getString("error_message"),
			result.getString("approval_id"),
			result.getInt("attempt_no"),
			result.getInt("max_attempts"),
			instant(result, "available_at"),
			result.getInt("priority"),
			result.getString("lease_owner"),
			result.getObject("lease_token", Long.class),
			instant(result, "lease_expires_at"),
			instant(result, "heartbeat_at"),
			result.getInt("version"),
			result.getInt("recovery_count"),
			result.getString("last_failure_code"),
			result.getString("recovery_policy") == null
				? ExecutionJob.RecoveryPolicy.MANUAL
				: ExecutionJob.RecoveryPolicy.valueOf(result.getString("recovery_policy")));
		return snapshot.value();
	}

	private void setJson(PreparedStatement statement, int index, Object value) throws Exception {
		if (value == null) {
			statement.setNull(index, java.sql.Types.OTHER);
		} else {
			statement.setString(index, mapper.writeValueAsString(value));
		}
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
		return new IllegalStateException("PostgreSQL job repository failed to " + operation, cause);
	}
}
