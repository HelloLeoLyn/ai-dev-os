package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.aidevos.orchestrator.persistence.postgresql.PostgresJdbc.RowReader;
import com.aidevos.orchestrator.project.Project;
import com.aidevos.orchestrator.project.ProjectRepository;
import com.aidevos.orchestrator.project.ProjectStatus;

/**
 * PostgreSQL implementation of the project repository backed by the projects
 * table (V9 + V23 migrations).
 */
final class PostgresProjectRepository implements ProjectRepository {

	private static final String COLUMNS = "project_id,name,path,description,status,"
		+ "repository_url,default_branch,created_at,updated_at";

	private final PostgresJdbc jdbc;

	PostgresProjectRepository(PostgresJdbc jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void save(Project project) {
		jdbc.update("INSERT INTO projects(" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?) "
			+ "ON CONFLICT(project_id) DO UPDATE SET name=EXCLUDED.name,path=EXCLUDED.path,"
			+ "description=EXCLUDED.description,status=EXCLUDED.status,"
			+ "repository_url=EXCLUDED.repository_url,default_branch=EXCLUDED.default_branch,"
			+ "updated_at=EXCLUDED.updated_at",
			project.getProjectId(), project.getName(), project.getPath(),
			project.getDescription(), project.getStatus().name(),
			project.getRepositoryUrl(), project.getDefaultBranch(),
			PostgresJdbc.timestamp(project.getCreatedAt()),
			PostgresJdbc.timestamp(project.getUpdatedAt()));
	}

	@Override
	public Project get(String projectId) {
		return jdbc.queryOne("SELECT " + COLUMNS + " FROM projects WHERE project_id=?",
			PostgresProjectRepository::read, projectId);
	}

	@Override
	public List<Project> list() {
		return jdbc.query("SELECT " + COLUMNS + " FROM projects ORDER BY created_at,project_id",
			PostgresProjectRepository::read);
	}

	@Override
	public boolean delete(String projectId) {
		return jdbc.updateReturnsRow("DELETE FROM projects WHERE project_id=?", projectId);
	}

	private static Project read(ResultSet result) throws SQLException {
		return new Project(result.getString("project_id"), result.getString("name"),
			result.getString("path"), result.getString("description"),
			ProjectStatus.valueOf(result.getString("status")),
			PostgresJdbc.instant(result, "created_at"),
			PostgresJdbc.instant(result, "updated_at"),
			result.getString("repository_url"), result.getString("default_branch"));
	}
}
