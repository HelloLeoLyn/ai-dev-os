package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.aidevos.orchestrator.change.ChangeRepository;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.change.ChangeStatus;

/**
 * PostgreSQL implementation of the change set repository backed by the
 * change_sets table (V16 migration).
 */
final class PostgresChangeRepository implements ChangeRepository {

	private static final String COLUMNS = "change_id,task_id,workspace_id,project_id,execution_id,"
		+ "branch,diff,diff_stat,files_changed,insertions,deletions,modified,added,deleted,"
		+ "status,reviewed_by,reviewed_at,created_at,updated_at";

	private final PostgresJdbc jdbc;

	PostgresChangeRepository(PostgresJdbc jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void save(ChangeSet change) {
		jdbc.update("INSERT INTO change_sets(" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,"
			+ "?,?,?,?,?,?) ON CONFLICT(change_id) DO UPDATE SET task_id=EXCLUDED.task_id,"
			+ "workspace_id=EXCLUDED.workspace_id,project_id=EXCLUDED.project_id,"
			+ "execution_id=EXCLUDED.execution_id,branch=EXCLUDED.branch,diff=EXCLUDED.diff,"
			+ "diff_stat=EXCLUDED.diff_stat,files_changed=EXCLUDED.files_changed,"
			+ "insertions=EXCLUDED.insertions,deletions=EXCLUDED.deletions,"
			+ "modified=EXCLUDED.modified,added=EXCLUDED.added,deleted=EXCLUDED.deleted,"
			+ "status=EXCLUDED.status,reviewed_by=EXCLUDED.reviewed_by,"
			+ "reviewed_at=EXCLUDED.reviewed_at,updated_at=EXCLUDED.updated_at",
			change.getChangeId(), change.getTaskId(), change.getWorkspaceId(),
			change.getProjectId(), change.getExecutionId(), change.getBranch(),
			change.getDiff(), change.getDiffStat(), change.getFilesChanged(),
			change.getInsertions(), change.getDeletions(), change.getModified(),
			change.getAdded(), change.getDeleted(), change.getStatus().name(),
			change.getReviewedBy(), PostgresJdbc.timestamp(change.getReviewedAt()),
			PostgresJdbc.timestamp(change.getCreatedAt()),
			PostgresJdbc.timestamp(change.getUpdatedAt()));
	}

	@Override
	public ChangeSet get(String changeId) {
		return jdbc.queryOne("SELECT " + COLUMNS + " FROM change_sets WHERE change_id=?",
			PostgresChangeRepository::read, changeId);
	}

	@Override
	public List<ChangeSet> getByTaskId(String taskId) {
		return jdbc.query("SELECT " + COLUMNS + " FROM change_sets WHERE task_id=?"
			+ " ORDER BY created_at,change_id", PostgresChangeRepository::read, taskId);
	}

	@Override
	public List<ChangeSet> list() {
		return jdbc.query("SELECT " + COLUMNS + " FROM change_sets ORDER BY created_at,change_id",
			PostgresChangeRepository::read);
	}

	private static ChangeSet read(ResultSet result) throws SQLException {
		return ChangeSet.restore(result.getString("change_id"), result.getString("task_id"),
			result.getString("workspace_id"), result.getString("project_id"),
			result.getString("execution_id"), result.getString("branch"),
			result.getString("diff"), result.getString("diff_stat"),
			result.getInt("files_changed"), result.getInt("insertions"),
			result.getInt("deletions"), result.getInt("modified"), result.getInt("added"),
			result.getInt("deleted"), ChangeStatus.valueOf(result.getString("status")),
			result.getString("reviewed_by"), PostgresJdbc.instant(result, "reviewed_at"),
			PostgresJdbc.instant(result, "created_at"),
			PostgresJdbc.instant(result, "updated_at"));
	}
}
