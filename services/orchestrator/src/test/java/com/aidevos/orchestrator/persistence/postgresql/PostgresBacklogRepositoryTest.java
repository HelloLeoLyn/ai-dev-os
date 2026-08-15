package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import com.aidevos.orchestrator.backlog.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresBacklogRepositoryTest {
	@Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
	private PGSimpleDataSource dataSource;
	@BeforeEach void setUp() throws Exception {
		dataSource = new PGSimpleDataSource(); dataSource.setUrl(POSTGRES.getJdbcUrl());
		dataSource.setUser(POSTGRES.getUsername()); dataSource.setPassword(POSTGRES.getPassword());
		new PostgresDocumentStore(dataSource, new ObjectMapper());
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
			statement.execute("DELETE FROM repository_documents WHERE repository_type='backlog-item'");
		}
	}
	@Test void restoresAllBacklogFieldsAfterRepositoryReinstantiation() {
		BacklogItem item = new BacklogItem("backlog-1", "Future", "Description", BacklogStatus.BLOCKED,
			BacklogPriority.CRITICAL, "project-1", "workspace-1", BacklogSourceType.LESSON,
			"LESSON-1", List.of("dependency-1"), List.of("security"), Instant.now());
		item.changeStatus(BacklogStatus.BLOCKED, "Waiting", Instant.now());
		item.setConvertedTaskId("task-1"); item.setCompletedAt(Instant.now());
		item.setRecommendationContext(new BacklogRecommendationContext("r1", "analysis-1", "source-task",
			"Goal", List.of("Done"), com.aidevos.orchestrator.analysis.AnalysisEnums.Level.HIGH,
			List.of("src"), com.aidevos.orchestrator.taskcenter.ExecutionMode.READ_WRITE, true));
		new PostgresBacklogRepository(dataSource, new ObjectMapper()).save(item);
		BacklogItem loaded = new PostgresBacklogRepository(dataSource, new ObjectMapper()).get("backlog-1");
		assertEquals(BacklogStatus.BLOCKED, loaded.getStatus()); assertEquals("Waiting", loaded.getBlockedReason());
		assertEquals(List.of("dependency-1"), loaded.getDependsOn()); assertEquals("task-1", loaded.getConvertedTaskId());
		assertNotNull(loaded.getCompletedAt()); assertEquals("project-1", loaded.getProjectId());
		assertEquals("workspace-1", loaded.getWorkspaceId());
		assertEquals("r1", loaded.getRecommendationContext().recommendationId());
		assertEquals(List.of("Done"), loaded.getRecommendationContext().acceptanceCriteria());
		assertEquals(1, new PostgresBacklogRepository(dataSource, new ObjectMapper()).listByProjectId("project-1").size());
	}
	@Test void readsOldBacklogJsonWithoutRecommendationContext() throws Exception {
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
			statement.execute("INSERT INTO repository_documents(repository_type,entity_id,payload) VALUES "
				+ "('backlog-item','legacy','{\"backlogItemId\":\"legacy\",\"title\":\"Legacy\","
				+ "\"status\":\"IDEA\",\"priority\":\"MEDIUM\",\"sourceType\":\"MANUAL\","
				+ "\"dependsOn\":[],\"tags\":[]}')");
		}
		assertNull(new PostgresBacklogRepository(dataSource, new ObjectMapper()).get("legacy").getRecommendationContext());
	}
}
