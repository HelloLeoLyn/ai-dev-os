package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.aidevos.orchestrator.ci.CiRepository;
import com.aidevos.orchestrator.ci.CiRunRecord;
import com.aidevos.orchestrator.ci.CiStatus;

/**
 * PostgreSQL implementation of the CI run repository backed by the ci_runs
 * table (V18 migration).
 */
final class PostgresCiRepository implements CiRepository {

	private static final String COLUMNS = "ci_run_id,task_id,pull_request_id,provider,"
		+ "pipeline_id,status,branch,commit_hash,report_url,started_at,finished_at";

	private final PostgresJdbc jdbc;

	PostgresCiRepository(PostgresJdbc jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void save(CiRunRecord record) {
		jdbc.update("INSERT INTO ci_runs(" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?) "
			+ "ON CONFLICT(ci_run_id) DO UPDATE SET task_id=EXCLUDED.task_id,"
			+ "pull_request_id=EXCLUDED.pull_request_id,provider=EXCLUDED.provider,"
			+ "pipeline_id=EXCLUDED.pipeline_id,status=EXCLUDED.status,"
			+ "branch=EXCLUDED.branch,commit_hash=EXCLUDED.commit_hash,"
			+ "report_url=EXCLUDED.report_url,finished_at=EXCLUDED.finished_at",
			record.getCiRunId(), record.getTaskId(), record.getPullRequestId(),
			record.getProvider(), record.getPipelineId(), record.getStatus().name(),
			record.getBranch(), record.getCommitHash(), record.getReportUrl(),
			PostgresJdbc.timestamp(record.getStartedAt()),
			PostgresJdbc.timestamp(record.getFinishedAt()));
	}

	@Override
	public CiRunRecord get(String ciRunId) {
		return jdbc.queryOne("SELECT " + COLUMNS + " FROM ci_runs WHERE ci_run_id=?",
			PostgresCiRepository::read, ciRunId);
	}

	@Override
	public List<CiRunRecord> getByTaskId(String taskId) {
		return jdbc.query("SELECT " + COLUMNS + " FROM ci_runs WHERE task_id=?"
			+ " ORDER BY started_at,ci_run_id", PostgresCiRepository::read, taskId);
	}

	@Override
	public List<CiRunRecord> getByPullRequestId(String pullRequestId) {
		return jdbc.query("SELECT " + COLUMNS + " FROM ci_runs WHERE pull_request_id=?"
			+ " ORDER BY started_at,ci_run_id", PostgresCiRepository::read, pullRequestId);
	}

	@Override
	public List<CiRunRecord> list() {
		return jdbc.query("SELECT " + COLUMNS + " FROM ci_runs ORDER BY started_at,ci_run_id",
			PostgresCiRepository::read);
	}

	private static CiRunRecord read(ResultSet result) throws SQLException {
		return CiRunRecord.restore(result.getString("ci_run_id"),
			result.getString("task_id"), result.getString("pull_request_id"),
			result.getString("provider"), result.getString("pipeline_id"),
			CiStatus.valueOf(result.getString("status")), result.getString("branch"),
			result.getString("commit_hash"), result.getString("report_url"),
			PostgresJdbc.instant(result, "started_at"),
			PostgresJdbc.instant(result, "finished_at"));
	}
}
