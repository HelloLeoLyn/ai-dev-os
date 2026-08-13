package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.validation.ValidationArtifact;
import com.aidevos.orchestrator.validation.ValidationCheck;
import com.aidevos.orchestrator.validation.ValidationCheckType;
import com.aidevos.orchestrator.validation.ValidationDecision;
import com.aidevos.orchestrator.validation.ValidationRun;
import com.aidevos.orchestrator.validation.ValidationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers(disabledWithoutDocker = true)
class PostgresValidationRepositoryIntegrationTest {
	@Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
	private PGSimpleDataSource dataSource;

	@BeforeEach void setUp() throws Exception {
		dataSource = new PGSimpleDataSource(); dataSource.setUrl(POSTGRES.getJdbcUrl());
		dataSource.setUser(POSTGRES.getUsername()); dataSource.setPassword(POSTGRES.getPassword());
		new PostgresDocumentStore(dataSource, new ObjectMapper());
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
			statement.execute("DELETE FROM repository_documents WHERE repository_type IN ('validation-run','validation-artifact')");
		}
	}

	@Test void runChecksDecisionAndArtifactSurviveRepositoryRestart() {
		PostgresDocumentStore store = new PostgresDocumentStore(dataSource, new ObjectMapper());
		PostgresValidationRepository runs = new PostgresValidationRepository(store);
		PostgresValidationArtifactRepository artifacts = new PostgresValidationArtifactRepository(store);
		ValidationCheck check = new ValidationCheck("check-1", ValidationCheckType.BACKEND_TEST,
			"Backend Test", true, true); check.setStatus(ValidationStatus.SUCCESS);
		check.setArtifactIds(List.of("artifact-1"));
		ValidationRun run = new ValidationRun("validation-1", "task-1", "project-1",
			"workspace-1", "plan-run-1", "execution-1");
		run.setStartedAt(Instant.now()); run.setCompletedAt(Instant.now());
		run.setStatus(ValidationStatus.SUCCESS); run.setDecision(ValidationDecision.PASS);
		run.setChecks(List.of(check)); runs.save(run);
		ValidationArtifact artifact = new ValidationArtifact(); artifact.setArtifactId("artifact-1");
		artifact.setValidationRunId("validation-1"); artifact.setCheckId("check-1");
		artifact.setTaskId("task-1"); artifact.setContent("real log"); artifact.setCreatedAt(Instant.now());
		artifacts.save(artifact);

		PostgresDocumentStore restartedStore = new PostgresDocumentStore(dataSource, new ObjectMapper());
		ValidationRun loaded = new PostgresValidationRepository(restartedStore).get("validation-1");
		ValidationArtifact loadedArtifact = new PostgresValidationArtifactRepository(restartedStore).get("artifact-1");
		assertEquals("task-1", loaded.getTaskId());
		assertEquals(ValidationDecision.PASS, loaded.getDecision());
		assertEquals(ValidationStatus.SUCCESS, loaded.getChecks().getFirst().getStatus());
		assertEquals(List.of("artifact-1"), loaded.getChecks().getFirst().getArtifactIds());
		assertNotNull(loadedArtifact); assertEquals("validation-1", loadedArtifact.getValidationRunId());
		assertEquals(1, new PostgresValidationRepository(restartedStore).findByTaskId("task-1").size());
	}
}
