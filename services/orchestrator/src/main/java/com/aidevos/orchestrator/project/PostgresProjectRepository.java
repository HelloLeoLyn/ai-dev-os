package com.aidevos.orchestrator.project;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL implementation of the project repository backed by the structured
 * projects table (V9 migration).
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
public class PostgresProjectRepository implements ProjectRepository {

	private static final String COLUMNS =
		"project_id,name,path,description,status,repository_url,default_branch,created_at,updated_at";

	private final DataSource dataSource;

	public PostgresProjectRepository(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public void save(Project project) {
		String sql = "INSERT INTO projects(" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?) "
			+ "ON CONFLICT(project_id) DO UPDATE SET name=EXCLUDED.name,path=EXCLUDED.path,"
			+ "description=EXCLUDED.description,status=EXCLUDED.status,"
			+ "repository_url=EXCLUDED.repository_url,default_branch=EXCLUDED.default_branch,"
			+ "updated_at=EXCLUDED.updated_at";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, project.getProjectId());
			statement.setString(2, project.getName());
			statement.setString(3, project.getPath());
			statement.setString(4, project.getDescription());
			statement.setString(5, project.getStatus().name());
			statement.setString(6, project.getRepositoryUrl());
			statement.setString(7, project.getDefaultBranch());
			statement.setTimestamp(8, Timestamp.from(project.getCreatedAt()));
			statement.setTimestamp(9, Timestamp.from(project.getUpdatedAt()));
			statement.executeUpdate();
		}
		catch (SQLException exception) {
			throw failure("save project", exception);
		}
	}

	@Override
	public Project get(String projectId) {
		return selectOne("SELECT " + COLUMNS + " FROM projects WHERE project_id=?", projectId);
	}

	@Override
	public List<Project> list() {
		return select("SELECT " + COLUMNS + " FROM projects ORDER BY created_at,project_id");
	}

	@Override
	public boolean delete(String projectId) {
		String sql = "DELETE FROM projects WHERE project_id=?";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, projectId);
			return statement.executeUpdate() == 1;
		}
		catch (SQLException exception) {
			throw failure("delete project", exception);
		}
	}

	private Project selectOne(String sql, Object... parameters) {
		List<Project> projects = select(sql, parameters);
		return projects.isEmpty() ? null : projects.getFirst();
	}

	private List<Project> select(String sql, Object... parameters) {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int index = 0; index < parameters.length; index++) {
				statement.setObject(index + 1, parameters[index]);
			}
			List<Project> projects = new ArrayList<>();
			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					projects.add(read(result));
				}
			}
			return projects;
		}
		catch (SQLException exception) {
			throw failure("list projects", exception);
		}
	}

	private Project read(ResultSet result) throws SQLException {
		return new Project(result.getString("project_id"), result.getString("name"),
			result.getString("path"), result.getString("description"),
			ProjectStatus.valueOf(result.getString("status")),
			result.getTimestamp("created_at").toInstant(),
			result.getTimestamp("updated_at").toInstant(),
			result.getString("repository_url"), result.getString("default_branch"));
	}

	private IllegalStateException failure(String operation, Exception cause) {
		return new IllegalStateException("PostgreSQL project repository failed to " + operation, cause);
	}
}
