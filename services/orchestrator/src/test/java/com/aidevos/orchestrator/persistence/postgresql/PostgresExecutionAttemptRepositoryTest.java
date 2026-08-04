package com.aidevos.orchestrator.persistence.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.execution.ExecutionAttempt;
import com.aidevos.orchestrator.execution.ExecutionAttemptStatus;
import com.aidevos.orchestrator.job.JobLease;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@Testcontainers(disabledWithoutDocker = true)
class PostgresExecutionAttemptRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	private PostgresExecutionAttemptRepository repository;

	@BeforeEach
	void setUp() {
		PGSimpleDataSource dataSource = new PGSimpleDataSource();
		dataSource.setUrl(POSTGRES.getJdbcUrl());
		dataSource.setUser(POSTGRES.getUsername());
		dataSource.setPassword(POSTGRES.getPassword());
		new PostgresDocumentStore(dataSource, new ObjectMapper());
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("DELETE FROM execution_attempts");
		}
		catch (SQLException exception) {
			throw new IllegalStateException(exception);
		}
		repository = new PostgresExecutionAttemptRepository(dataSource);
	}

	@Test
	void saveAndGetRoundTrip() {
		ExecutionAttempt attempt = ExecutionAttempt.restore("attempt-1", "job-1", 1, null,
			ExecutionAttemptStatus.STARTING, null, null, null, null, null, 0, NOW, null, null);
		repository.save(attempt);

		ExecutionAttempt stored = repository.get("attempt-1");
		assertEquals("attempt-1", stored.getId());
		assertEquals("job-1", stored.getJobId());
		assertEquals(1, stored.getAttemptNo());
		assertEquals(ExecutionAttemptStatus.STARTING, stored.getStatus());
		assertEquals(NOW, stored.getCreatedAt());
		assertNull(repository.get("missing"));
	}

	@Test
	void saveUpdatesExistingAttempt() {
		ExecutionAttempt attempt = new ExecutionAttempt("attempt-1", "job-1", 1);
		attempt.markRunning(NOW);
		attempt.setExecutionId("exec-1");
		attempt.applyLease(new JobLease("worker-1", 1, NOW.plusSeconds(60)));
		repository.save(attempt);
		attempt.markSucceeded(NOW.plusSeconds(5));
		repository.save(attempt);

		ExecutionAttempt stored = repository.get("attempt-1");
		assertEquals(ExecutionAttemptStatus.SUCCEEDED, stored.getStatus());
		assertEquals("exec-1", stored.getExecutionId());
		assertEquals(NOW.plusSeconds(5), stored.getCompletedAt());
	}

	@Test
	void getByJobReturnsAttemptsOrderedByAttemptNo() {
		repository.save(new ExecutionAttempt("attempt-1", "job-1", 1));
		repository.save(new ExecutionAttempt("attempt-2", "job-1", 2));
		repository.save(new ExecutionAttempt("attempt-3", "job-2", 1));

		List<ExecutionAttempt> attempts = repository.getByJob("job-1");
		assertEquals(List.of("attempt-1", "attempt-2"),
			attempts.stream().map(ExecutionAttempt::getId).toList());
	}

	@Test
	void listActiveIncludesOnlyStartingAndRunning() {
		ExecutionAttempt starting = new ExecutionAttempt("attempt-1", "job-1", 1);
		ExecutionAttempt running = new ExecutionAttempt("attempt-2", "job-1", 2);
		running.markRunning(NOW);
		ExecutionAttempt succeeded = new ExecutionAttempt("attempt-3", "job-1", 3);
		succeeded.markRunning(NOW);
		succeeded.markSucceeded(NOW.plusSeconds(1));
		repository.save(starting);
		repository.save(running);
		repository.save(succeeded);

		assertEquals(List.of("attempt-1", "attempt-2"),
			repository.listActive().stream().map(ExecutionAttempt::getId).toList());
	}

	@Test
	void findAbandonedReturnsOnlyRunningAttemptsWithExpiredLease() {
		ExecutionAttempt expired = new ExecutionAttempt("attempt-1", "job-1", 1);
		expired.markRunning(NOW);
		expired.applyLease(new JobLease("worker-1", 1, NOW.minusSeconds(30)));

		ExecutionAttempt alive = new ExecutionAttempt("attempt-2", "job-1", 2);
		alive.markRunning(NOW);
		alive.applyLease(new JobLease("worker-1", 1, NOW.plusSeconds(30)));

		ExecutionAttempt noLease = new ExecutionAttempt("attempt-3", "job-1", 3);
		noLease.markRunning(NOW);

		repository.save(expired);
		repository.save(alive);
		repository.save(noLease);

		assertEquals(List.of("attempt-1"),
			repository.findAbandoned(NOW).stream().map(ExecutionAttempt::getId).toList());
	}
}
