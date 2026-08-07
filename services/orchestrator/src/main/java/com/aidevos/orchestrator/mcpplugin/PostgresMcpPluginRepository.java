package com.aidevos.orchestrator.mcpplugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL implementation of the MCP plugin repository backed by the
 * structured mcp_plugins table (V12 migration).
 */
@Repository
@DependsOn("postgresDocumentStore")
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
public class PostgresMcpPluginRepository implements McpPluginRepository {

	private static final String COLUMNS = "plugin_id,enabled,permission_level";

	private final DataSource dataSource;

	public PostgresMcpPluginRepository(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public void save(McpPlugin plugin) {
		String sql = "INSERT INTO mcp_plugins(" + COLUMNS + ") VALUES (?,?,?) "
			+ "ON CONFLICT(plugin_id) DO UPDATE SET enabled=EXCLUDED.enabled,"
			+ "permission_level=EXCLUDED.permission_level,updated_at=CURRENT_TIMESTAMP";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, plugin.getPluginId());
			statement.setBoolean(2, plugin.isEnabled());
			statement.setString(3, plugin.getPermissionLevel());
			statement.executeUpdate();
		}
		catch (SQLException exception) {
			throw failure("save plugin", exception);
		}
	}

	@Override
	public McpPlugin get(String pluginId) {
		return selectOne("SELECT " + COLUMNS + " FROM mcp_plugins WHERE plugin_id=?", pluginId);
	}

	@Override
	public List<McpPlugin> list() {
		return select("SELECT " + COLUMNS + " FROM mcp_plugins ORDER BY plugin_id");
	}

	@Override
	public boolean delete(String pluginId) {
		String sql = "DELETE FROM mcp_plugins WHERE plugin_id=?";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, pluginId);
			return statement.executeUpdate() == 1;
		}
		catch (SQLException exception) {
			throw failure("delete plugin", exception);
		}
	}

	private McpPlugin selectOne(String sql, Object... parameters) {
		List<McpPlugin> plugins = select(sql, parameters);
		return plugins.isEmpty() ? null : plugins.getFirst();
	}

	private List<McpPlugin> select(String sql, Object... parameters) {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int index = 0; index < parameters.length; index++) {
				statement.setObject(index + 1, parameters[index]);
			}
			List<McpPlugin> plugins = new ArrayList<>();
			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					plugins.add(read(result));
				}
			}
			return plugins;
		}
		catch (SQLException exception) {
			throw failure("list plugins", exception);
		}
	}

	private McpPlugin read(ResultSet result) throws SQLException {
		return new McpPlugin(result.getString("plugin_id"), null, null, null,
			result.getString("permission_level"), result.getBoolean("enabled"), List.of());
	}

	private IllegalStateException failure(String operation, Exception cause) {
		return new IllegalStateException("PostgreSQL MCP plugin repository failed to " + operation, cause);
	}
}
