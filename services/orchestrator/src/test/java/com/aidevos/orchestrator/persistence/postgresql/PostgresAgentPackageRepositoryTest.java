package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import com.aidevos.orchestrator.agentmarket.AgentPackage;
import com.aidevos.orchestrator.agentmarket.PostgresAgentPackageRepository;
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
class PostgresAgentPackageRepositoryTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	private PostgresAgentPackageRepository repository;

	@BeforeEach
	void setUp() {
		PGSimpleDataSource dataSource = new PGSimpleDataSource();
		dataSource.setUrl(POSTGRES.getJdbcUrl());
		dataSource.setUser(POSTGRES.getUsername());
		dataSource.setPassword(POSTGRES.getPassword());
		new PostgresDocumentStore(dataSource, new ObjectMapper());
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("DELETE FROM agent_packages");
		}
		catch (SQLException exception) {
			throw new IllegalStateException(exception);
		}
		repository = new PostgresAgentPackageRepository(dataSource);
	}

	@Test
	void saveAndGetRoundTrip() {
		repository.save(package_("coder-agent", "1.0.0", false, true));

		AgentPackage stored = repository.get("coder-agent");
		assertEquals("coder-agent", stored.getAgentId());
		assertEquals("1.0.0", stored.getVersion());
		assertFalse(stored.isInstalled());
		assertTrue(stored.isEnabled());
		assertNull(repository.get("missing"));
	}

	@Test
	void saveUpdatesInstallState() {
		repository.save(package_("coder-agent", "1.0.0", false, true));
		repository.save(package_("coder-agent", "2.0.0", true, false));

		AgentPackage stored = repository.get("coder-agent");
		assertEquals("2.0.0", stored.getVersion());
		assertTrue(stored.isInstalled());
		assertFalse(stored.isEnabled());
	}

	@Test
	void listReturnsAllPackagesOrdered() {
		repository.save(package_("tester-agent", "1.0.0", false, true));
		repository.save(package_("coder-agent", "1.0.0", false, true));

		List<AgentPackage> stored = repository.list();

		assertEquals(List.of("coder-agent", "tester-agent"),
			stored.stream().map(AgentPackage::getAgentId).toList());
	}

	@Test
	void deleteRemovesPackage() {
		repository.save(package_("coder-agent", "1.0.0", false, true));

		assertTrue(repository.delete("coder-agent"));
		assertNull(repository.get("coder-agent"));
		assertFalse(repository.delete("coder-agent"));
	}

	private AgentPackage package_(String agentId, String version, boolean installed,
			boolean enabled) {
		return new AgentPackage(agentId, null, version, null, null, List.of(), List.of(),
			List.of(), "mock", null, enabled, installed);
	}
}
