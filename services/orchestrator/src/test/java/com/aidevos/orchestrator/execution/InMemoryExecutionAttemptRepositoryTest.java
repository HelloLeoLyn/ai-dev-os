package com.aidevos.orchestrator.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.job.JobLease;
import org.junit.jupiter.api.Test;

class InMemoryExecutionAttemptRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

	@Test
	void saveAndGetRoundTrip() {
		InMemoryExecutionAttemptRepository repository = new InMemoryExecutionAttemptRepository();
		ExecutionAttempt attempt = new ExecutionAttempt("attempt-1", "job-1", 1);
		repository.save(attempt);

		assertEquals("attempt-1", repository.get("attempt-1").getId());
		assertNull(repository.get("missing"));
	}

	@Test
	void getByJobReturnsAttemptsOrderedByAttemptNo() {
		InMemoryExecutionAttemptRepository repository = new InMemoryExecutionAttemptRepository();
		repository.save(new ExecutionAttempt("attempt-1", "job-1", 1));
		repository.save(new ExecutionAttempt("attempt-2", "job-1", 2));
		repository.save(new ExecutionAttempt("attempt-3", "job-2", 1));

		List<ExecutionAttempt> attempts = repository.getByJob("job-1");
		assertEquals(List.of("attempt-1", "attempt-2"),
			attempts.stream().map(ExecutionAttempt::getId).toList());
	}

	@Test
	void listActiveIncludesOnlyStartingAndRunning() {
		InMemoryExecutionAttemptRepository repository = new InMemoryExecutionAttemptRepository();
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
		InMemoryExecutionAttemptRepository repository = new InMemoryExecutionAttemptRepository();
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
