package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.persistence.postgresql.PostgresJdbc.RowReader;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceRepository;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;

/**
 * PostgreSQL implementation of the workspace repository backed by the
 * workspaces table (V13 migration).
 */
final class PostgresWorkspaceRepository implements WorkspaceRepository {

	private static final String COLUMNS = "workspace_id,project_id,path,branch,repository_url,"
		+ "status,created_at,updated_at";

	private final PostgresJdbc jdbc;

	PostgresWorkspaceRepository(PostgresJdbc jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void save(Workspace workspace) {
		jdbc.update("INSERT INTO workspaces(" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?) "
			+ "ON CONFLICT(workspace_id) DO UPDATE SET project_id=EXCLUDED.project_id,"
			+ "path=EXCLUDED.path,branch=EXCLUDED.branch,"
			+ "repository_url=EXCLUDED.repository_url,status=EXCLUDED.status,"
			+ "updated_at=EXCLUDED.updated_at",
			workspace.getWorkspaceId(), workspace.getProjectId(), workspace.getPath(),
			workspace.getBranch(), workspace.getRepositoryUrl(),
			workspace.getStatus().name(), PostgresJdbc.timestamp(workspace.getCreatedAt()),
			PostgresJdbc.timestamp(workspace.getUpdatedAt()));
	}

	@Override
	public Workspace get(String workspaceId) {
		return jdbc.queryOne("SELECT " + COLUMNS + " FROM workspaces WHERE workspace_id=?",
			PostgresWorkspaceRepository::read, workspaceId);
	}

	@Override
	public Optional<Workspace> getByProjectId(String projectId) {
		return Optional.ofNullable(jdbc.queryOne(
			"SELECT " + COLUMNS + " FROM workspaces WHERE project_id=? ORDER BY created_at,"
				+ "workspace_id LIMIT 1", PostgresWorkspaceRepository::read, projectId));
	}

	@Override
	public List<Workspace> listByProjectId(String projectId) {
		return jdbc.query("SELECT " + COLUMNS + " FROM workspaces WHERE project_id=?"
			+ " ORDER BY created_at,workspace_id", PostgresWorkspaceRepository::read, projectId);
	}

	@Override
	public List<Workspace> list() {
		return jdbc.query("SELECT " + COLUMNS + " FROM workspaces ORDER BY created_at,workspace_id",
			PostgresWorkspaceRepository::read);
	}

	@Override
	public boolean delete(String workspaceId) {
		return jdbc.updateReturnsRow("DELETE FROM workspaces WHERE workspace_id=?", workspaceId);
	}

	private static Workspace read(ResultSet result) throws SQLException {
		return new Workspace(result.getString("workspace_id"), result.getString("project_id"),
			result.getString("path"), result.getString("branch"),
			WorkspaceStatus.valueOf(result.getString("status")),
			PostgresJdbc.instant(result, "created_at"),
			PostgresJdbc.instant(result, "updated_at"),
			result.getString("repository_url"));
	}
}
