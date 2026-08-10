package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.aidevos.orchestrator.commit.CommitRecord;
import com.aidevos.orchestrator.commit.CommitRepository;
import com.aidevos.orchestrator.commit.CommitStatus;

/**
 * PostgreSQL implementation of the commit record repository backed by the
 * commits table (V17 migration).
 */
final class PostgresCommitRepository implements CommitRepository {

	private static final String COLUMNS = "commit_id,change_id,task_id,workspace_id,branch,"
		+ "message,git_hash,status,created_at,updated_at";

	private final PostgresJdbc jdbc;

	PostgresCommitRepository(PostgresJdbc jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void save(CommitRecord record) {
		jdbc.update("INSERT INTO commits(" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?) "
			+ "ON CONFLICT(commit_id) DO UPDATE SET change_id=EXCLUDED.change_id,"
			+ "task_id=EXCLUDED.task_id,workspace_id=EXCLUDED.workspace_id,"
			+ "branch=EXCLUDED.branch,message=EXCLUDED.message,git_hash=EXCLUDED.git_hash,"
			+ "status=EXCLUDED.status,updated_at=EXCLUDED.updated_at",
			record.getCommitId(), record.getChangeId(), record.getTaskId(),
			record.getWorkspaceId(), record.getBranch(), record.getMessage(),
			record.getGitHash(), record.getStatus().name(),
			PostgresJdbc.timestamp(record.getCreatedAt()),
			PostgresJdbc.timestamp(record.getUpdatedAt()));
	}

	@Override
	public CommitRecord get(String commitId) {
		return jdbc.queryOne("SELECT " + COLUMNS + " FROM commits WHERE commit_id=?",
			PostgresCommitRepository::read, commitId);
	}

	@Override
	public List<CommitRecord> getByTaskId(String taskId) {
		return jdbc.query("SELECT " + COLUMNS + " FROM commits WHERE task_id=?"
			+ " ORDER BY created_at,commit_id", PostgresCommitRepository::read, taskId);
	}

	@Override
	public List<CommitRecord> list() {
		return jdbc.query("SELECT " + COLUMNS + " FROM commits ORDER BY created_at,commit_id",
			PostgresCommitRepository::read);
	}

	private static CommitRecord read(ResultSet result) throws SQLException {
		return CommitRecord.restore(result.getString("commit_id"),
			result.getString("change_id"), result.getString("task_id"),
			result.getString("workspace_id"), result.getString("branch"),
			result.getString("message"), CommitStatus.valueOf(result.getString("status")),
			result.getString("git_hash"), PostgresJdbc.instant(result, "created_at"),
			PostgresJdbc.instant(result, "updated_at"));
	}
}
