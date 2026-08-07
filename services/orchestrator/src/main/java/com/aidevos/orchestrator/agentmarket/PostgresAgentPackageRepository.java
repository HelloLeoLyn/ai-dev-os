package com.aidevos.orchestrator.agentmarket;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL implementation of the agent package repository backed by the
 * structured agent_packages table (V11 migration).
 */
@Repository
@DependsOn("postgresDocumentStore")
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
public class PostgresAgentPackageRepository implements AgentPackageRepository {

	private static final String COLUMNS = "agent_id,version,installed,enabled";

	private final DataSource dataSource;

	public PostgresAgentPackageRepository(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public void save(AgentPackage agentPackage) {
		String sql = "INSERT INTO agent_packages(" + COLUMNS + ") VALUES (?,?,?,?) "
			+ "ON CONFLICT(agent_id) DO UPDATE SET version=EXCLUDED.version,"
			+ "installed=EXCLUDED.installed,enabled=EXCLUDED.enabled,"
			+ "updated_at=CURRENT_TIMESTAMP";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, agentPackage.getAgentId());
			statement.setString(2, agentPackage.getVersion());
			statement.setBoolean(3, agentPackage.isInstalled());
			statement.setBoolean(4, agentPackage.isEnabled());
			statement.executeUpdate();
		}
		catch (SQLException exception) {
			throw failure("save agent package", exception);
		}
	}

	@Override
	public AgentPackage get(String agentId) {
		return selectOne("SELECT " + COLUMNS + " FROM agent_packages WHERE agent_id=?", agentId);
	}

	@Override
	public List<AgentPackage> list() {
		return select("SELECT " + COLUMNS + " FROM agent_packages ORDER BY agent_id");
	}

	@Override
	public boolean delete(String agentId) {
		String sql = "DELETE FROM agent_packages WHERE agent_id=?";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, agentId);
			return statement.executeUpdate() == 1;
		}
		catch (SQLException exception) {
			throw failure("delete agent package", exception);
		}
	}

	private AgentPackage selectOne(String sql, Object... parameters) {
		List<AgentPackage> packages = select(sql, parameters);
		return packages.isEmpty() ? null : packages.getFirst();
	}

	private List<AgentPackage> select(String sql, Object... parameters) {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int index = 0; index < parameters.length; index++) {
				statement.setObject(index + 1, parameters[index]);
			}
			List<AgentPackage> packages = new ArrayList<>();
			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					packages.add(read(result));
				}
			}
			return packages;
		}
		catch (SQLException exception) {
			throw failure("list agent packages", exception);
		}
	}

	private AgentPackage read(ResultSet result) throws SQLException {
		return new AgentPackage(result.getString("agent_id"), null,
			result.getString("version"), null, null, List.of(), List.of(), List.of(),
			"mock", new LinkedHashMap<>(), result.getBoolean("enabled"),
			result.getBoolean("installed"));
	}

	private IllegalStateException failure(String operation, Exception cause) {
		return new IllegalStateException("PostgreSQL agent package repository failed to " + operation, cause);
	}
}
