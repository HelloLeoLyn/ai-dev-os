package com.aidevos.orchestrator.execution;

import java.time.Instant;

import com.aidevos.orchestrator.job.JobLease;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionAttemptRecoveryTest {

	private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

	@Test
	void marksStaleRunningAttemptRecoveryRequired() {
		InMemoryExecutionAttemptRepository repository = new InMemoryExecutionAttemptRepository();
		ExecutionAttempt stale = runningAttempt("attempt-1", "job-1", 1, NOW.minusSeconds(30));
		repository.save(stale);

		ExecutionAttemptRecovery recovery = new ExecutionAttemptRecovery(repository);
		int recovered = recovery.recoverStale(NOW);

		assertEquals(1, recovered);
		ExecutionAttempt stored = repository.get("attempt-1");
		assertEquals(ExecutionAttemptStatus.RECOVERY_REQUIRED, stored.getStatus());
		assertEquals(ExecutionAttemptRecovery.STALE_EXECUTION, stored.getFailureCode());
		assertNotNull(stored.getCompletedAt());
		assertEquals(NOW, stored.getCompletedAt());
	}

	@Test
	void ignoresAttemptWithActiveLease() {
		InMemoryExecutionAttemptRepository repository = new InMemoryExecutionAttemptRepository();
		ExecutionAttempt alive = runningAttempt("attempt-1", "job-1", 1, NOW.plusSeconds(30));
		repository.save(alive);

		ExecutionAttemptRecovery recovery = new ExecutionAttemptRecovery(repository);
		int recovered = recovery.recoverStale(NOW);

		assertEquals(0, recovered);
		assertEquals(ExecutionAttemptStatus.RUNNING, repository.get("attempt-1").getStatus());
	}

	@Test
	void ignoresTerminalAndNonRunningAttempts() {
		InMemoryExecutionAttemptRepository repository = new InMemoryExecutionAttemptRepository();
		ExecutionAttempt succeeded = runningAttempt("attempt-1", "job-1", 1, NOW.minusSeconds(30));
		succeeded.markSucceeded(NOW);
		repository.save(succeeded);

		ExecutionAttempt starting = new ExecutionAttempt("attempt-2", "job-1", 2);
		repository.save(starting);

		ExecutionAttemptRecovery recovery = new ExecutionAttemptRecovery(repository);
		int recovered = recovery.recoverStale(NOW);

		assertEquals(0, recovered);
		assertEquals(ExecutionAttemptStatus.SUCCEEDED, repository.get("attempt-1").getStatus());
		assertEquals(ExecutionAttemptStatus.STARTING, repository.get("attempt-2").getStatus());
	}

	@Test
	void isIdempotent() {
		InMemoryExecutionAttemptRepository repository = new InMemoryExecutionAttemptRepository();
		repository.save(runningAttempt("attempt-1", "job-1", 1, NOW.minusSeconds(30)));

		ExecutionAttemptRecovery recovery = new ExecutionAttemptRecovery(repository);
		assertEquals(1, recovery.recoverStale(NOW));
		assertEquals(0, recovery.recoverStale(NOW));
		assertEquals(ExecutionAttemptStatus.RECOVERY_REQUIRED, repository.get("attempt-1").getStatus());
		assertNull(repository.get("attempt-1").getLeaseOwner());
	}

	@Test
	void recoversMultipleStaleAttempts() {
		InMemoryExecutionAttemptRepository repository = new InMemoryExecutionAttemptRepository();
		repository.save(runningAttempt("attempt-1", "job-1", 1, NOW.minusSeconds(30)));
		repository.save(runningAttempt("attempt-2", "job-2", 1, NOW.minusSeconds(60)));

		ExecutionAttemptRecovery recovery = new ExecutionAttemptRecovery(repository);
		int recovered = recovery.recoverStale(NOW);

		assertEquals(2, recovered);
		assertTrue(repository.findAbandoned(NOW).isEmpty());
		assertEquals(ExecutionAttemptStatus.RECOVERY_REQUIRED,
			repository.get("attempt-1").getStatus());
		assertEquals(ExecutionAttemptStatus.RECOVERY_REQUIRED,
			repository.get("attempt-2").getStatus());
		assertNull(repository.get("attempt-1").getLeaseOwner());
		assertNull(repository.get("attempt-2").getLeaseOwner());
	}

	private ExecutionAttempt runningAttempt(String id, String jobId, int attemptNo,
			Instant leaseExpiry) {
		ExecutionAttempt attempt = new ExecutionAttempt(id, jobId, attemptNo);
		attempt.markRunning(NOW);
		attempt.applyLease(new JobLease("worker-1", 1, leaseExpiry));
		return attempt;
	}
}
