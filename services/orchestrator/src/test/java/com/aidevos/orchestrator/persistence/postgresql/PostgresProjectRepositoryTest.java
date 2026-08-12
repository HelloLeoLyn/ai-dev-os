package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.project.PostgresProjectRepository;
import com.aidevos.orchestrator.project.Project;
import com.aidevos.orchestrator.project.ProjectStatus;
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
class PostgresProjectRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	private PostgresProjectRepository repository;

	@BeforeEach
	void setUp() {
		PGSimpleDataSource dataSource = new PGSimpleDataSource();
		dataSource.setUrl(POSTGRES.getJdbcUrl());
		dataSource.setUser(POSTGRES.getUsername());
		dataSource.setPassword(POSTGRES.getPassword());
		new PostgresDocumentStore(dataSource, new ObjectMapper());
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("DELETE FROM projects");
		}
		catch (SQLException exception) {
			throw new IllegalStateException(exception);
		}
		repository = new PostgresProjectRepository(dataSource);
	}

	@Test
	void saveAndGetRoundTrip() {
		repository.save(new Project("project-1", "AI Dev OS", "/workspace/ai-dev-os",
			"main platform", ProjectStatus.ACTIVE, NOW, NOW,
			"git@github.com:example/ai-dev-os.git", "dev"));

		Project stored = repository.get("project-1");
		assertEquals("project-1", stored.getProjectId());
		assertEquals("AI Dev OS", stored.getName());
		assertEquals("/workspace/ai-dev-os", stored.getPath());
		assertEquals("main platform", stored.getDescription());
		assertEquals(ProjectStatus.ACTIVE, stored.getStatus());
		assertEquals("git@github.com:example/ai-dev-os.git", stored.getRepositoryUrl());
		assertEquals("dev", stored.getDefaultBranch());
		assertEquals(NOW, stored.getCreatedAt());
		assertEquals(NOW, stored.getUpdatedAt());
		assertNull(repository.get("missing"));
	}

	@Test
	void saveAllowsProjectWithoutRemoteOrBranch() {
		repository.save(project("project-local", "Local", "/p/local", null,
			ProjectStatus.ACTIVE, NOW));

		Project stored = repository.get("project-local");

		assertNull(stored.getRepositoryUrl());
		assertNull(stored.getDefaultBranch());
	}

	@Test
	void saveUpdatesExistingProject() {
		repository.save(project("project-1", "Old", "/p/old", null, ProjectStatus.ACTIVE, NOW));
		Project updated = project("project-1", "New", "/p/new", "updated",
			ProjectStatus.ARCHIVED, NOW.plusSeconds(60));

		repository.save(updated);

		Project stored = repository.get("project-1");
		assertEquals("New", stored.getName());
		assertEquals("/p/new", stored.getPath());
		assertEquals(ProjectStatus.ARCHIVED, stored.getStatus());
		assertEquals(NOW.plusSeconds(60), stored.getUpdatedAt());
	}

	@Test
	void listReturnsAllProjectsOrdered() {
		repository.save(project("project-2", "Two", "/p/2", null, ProjectStatus.ACTIVE,
			NOW.plusSeconds(10)));
		repository.save(project("project-1", "One", "/p/1", null, ProjectStatus.ACTIVE, NOW));

		List<Project> projects = repository.list();

		assertEquals(List.of("project-1", "project-2"),
			projects.stream().map(Project::getProjectId).toList());
	}

	@Test
	void deleteRemovesProject() {
		repository.save(project("project-1", "One", "/p/1", null, ProjectStatus.ACTIVE, NOW));

		assertTrue(repository.delete("project-1"));
		assertNull(repository.get("project-1"));
		assertFalse(repository.delete("project-1"));
	}

	private Project project(String projectId, String name, String path, String description,
			ProjectStatus status, Instant timestamp) {
		return new Project(projectId, name, path, description, status, timestamp, timestamp);
	}
}
