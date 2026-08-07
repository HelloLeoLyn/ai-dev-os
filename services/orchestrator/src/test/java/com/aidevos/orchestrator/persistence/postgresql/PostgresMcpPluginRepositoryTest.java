package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import com.aidevos.orchestrator.mcpplugin.McpPlugin;
import com.aidevos.orchestrator.mcpplugin.PostgresMcpPluginRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class PostgresMcpPluginRepositoryTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	private PostgresMcpPluginRepository repository;

	@BeforeEach
	void setUp() {
		PGSimpleDataSource dataSource = new PGSimpleDataSource();
		dataSource.setUrl(POSTGRES.getJdbcUrl());
		dataSource.setUser(POSTGRES.getUsername());
		dataSource.setPassword(POSTGRES.getPassword());
		new PostgresDocumentStore(dataSource, new ObjectMapper());
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("DELETE FROM mcp_plugins");
		}
		catch (SQLException exception) {
			throw new IllegalStateException(exception);
		}
		repository = new PostgresMcpPluginRepository(dataSource);
	}

	@Test
	void saveAndGetRoundTrip() {
		repository.save(plugin("filesystem", true, McpPlugin.PERMISSION_READ_ONLY));

		McpPlugin stored = repository.get("filesystem");
		assertEquals("filesystem", stored.getPluginId());
		assertTrue(stored.isEnabled());
		assertEquals(McpPlugin.PERMISSION_READ_ONLY, stored.getPermissionLevel());
		assertNull(repository.get("missing"));
	}

	@Test
	void saveUpdatesEnabledState() {
		repository.save(plugin("docker", true, McpPlugin.PERMISSION_WORKSPACE_WRITE));

		McpPlugin disabled = new McpPlugin("docker", null, null, null,
			McpPlugin.PERMISSION_WORKSPACE_WRITE, false, List.of());
		repository.save(disabled);

		McpPlugin stored = repository.get("docker");
		assertFalse(stored.isEnabled());
		assertEquals(McpPlugin.PERMISSION_WORKSPACE_WRITE, stored.getPermissionLevel());
	}

	@Test
	void listReturnsAllPluginsOrdered() {
		repository.save(plugin("git", true, McpPlugin.PERMISSION_READ_ONLY));
		repository.save(plugin("filesystem", true, McpPlugin.PERMISSION_READ_ONLY));

		List<McpPlugin> stored = repository.list();

		assertEquals(List.of("filesystem", "git"),
			stored.stream().map(McpPlugin::getPluginId).toList());
	}

	@Test
	void deleteRemovesPlugin() {
		repository.save(plugin("filesystem", true, McpPlugin.PERMISSION_READ_ONLY));

		assertTrue(repository.delete("filesystem"));
		assertNull(repository.get("filesystem"));
		assertFalse(repository.delete("filesystem"));
	}

	private McpPlugin plugin(String pluginId, boolean enabled, String permissionLevel) {
		return new McpPlugin(pluginId, null, null, null, permissionLevel, enabled, List.of());
	}
}
