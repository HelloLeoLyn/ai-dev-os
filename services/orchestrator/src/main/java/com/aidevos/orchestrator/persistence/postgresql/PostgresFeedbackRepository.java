package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.aidevos.orchestrator.feedback.FeedbackRepository;
import com.aidevos.orchestrator.feedback.FeedbackStatus;
import com.aidevos.orchestrator.feedback.PrFeedbackRecord;

/**
 * PostgreSQL implementation of the PR feedback repository backed by the
 * pr_feedback table (V20 migration).
 */
final class PostgresFeedbackRepository implements FeedbackRepository {

	private static final String COLUMNS = "feedback_id,task_id,pull_request_id,repair_task_id,"
		+ "change_id,commit_id,ci_run_id,status,retry_count,created_at,updated_at";

	private final PostgresJdbc jdbc;

	PostgresFeedbackRepository(PostgresJdbc jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void save(PrFeedbackRecord record) {
		jdbc.update("INSERT INTO pr_feedback(" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?) "
			+ "ON CONFLICT(feedback_id) DO UPDATE SET task_id=EXCLUDED.task_id,"
			+ "pull_request_id=EXCLUDED.pull_request_id,repair_task_id=EXCLUDED.repair_task_id,"
			+ "change_id=EXCLUDED.change_id,commit_id=EXCLUDED.commit_id,"
			+ "ci_run_id=EXCLUDED.ci_run_id,status=EXCLUDED.status,"
			+ "retry_count=EXCLUDED.retry_count,updated_at=EXCLUDED.updated_at",
			record.getFeedbackId(), record.getTaskId(), record.getPullRequestId(),
			record.getRepairTaskId(), record.getChangeId(), record.getCommitId(),
			record.getCiRunId(), record.getStatus().name(), record.getRetryCount(),
			PostgresJdbc.timestamp(record.getCreatedAt()),
			PostgresJdbc.timestamp(record.getUpdatedAt()));
	}

	@Override
	public PrFeedbackRecord get(String feedbackId) {
		return jdbc.queryOne("SELECT " + COLUMNS + " FROM pr_feedback WHERE feedback_id=?",
			PostgresFeedbackRepository::read, feedbackId);
	}

	@Override
	public List<PrFeedbackRecord> getByTaskId(String taskId) {
		return jdbc.query("SELECT " + COLUMNS + " FROM pr_feedback WHERE task_id=?"
			+ " ORDER BY created_at,feedback_id", PostgresFeedbackRepository::read, taskId);
	}

	@Override
	public List<PrFeedbackRecord> getByPullRequestId(String pullRequestId) {
		return jdbc.query("SELECT " + COLUMNS + " FROM pr_feedback WHERE pull_request_id=?"
			+ " ORDER BY created_at,feedback_id", PostgresFeedbackRepository::read, pullRequestId);
	}

	@Override
	public List<PrFeedbackRecord> getByCiRunId(String ciRunId) {
		return jdbc.query("SELECT " + COLUMNS + " FROM pr_feedback WHERE ci_run_id=?"
			+ " ORDER BY created_at,feedback_id", PostgresFeedbackRepository::read, ciRunId);
	}

	@Override
	public List<PrFeedbackRecord> list() {
		return jdbc.query("SELECT " + COLUMNS + " FROM pr_feedback ORDER BY created_at,feedback_id",
			PostgresFeedbackRepository::read);
	}

	private static PrFeedbackRecord read(ResultSet result) throws SQLException {
		return PrFeedbackRecord.restore(result.getString("feedback_id"),
			result.getString("task_id"), result.getString("pull_request_id"),
			result.getString("repair_task_id"), result.getString("change_id"),
			result.getString("commit_id"), result.getString("ci_run_id"),
			FeedbackStatus.valueOf(result.getString("status")), result.getInt("retry_count"),
			PostgresJdbc.instant(result, "created_at"),
			PostgresJdbc.instant(result, "updated_at"));
	}
}
