package com.aidevos.orchestrator.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import com.aidevos.orchestrator.job.JobLease;
import org.junit.jupiter.api.Test;

class ExecutionAttemptTest {

	private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

	private ExecutionAttempt newAttempt() {
		return new ExecutionAttempt("attempt-1", "job-1", 1);
	}

	@Test
	void newAttemptDefaultsToStarting() {
		ExecutionAttempt attempt = newAttempt();
		assertEquals(ExecutionAttemptStatus.STARTING, attempt.getStatus());
		assertEquals(0, attempt.getRecoveryCount());
		assertNull(attempt.getExecutionId());
		assertNull(attempt.getLeaseOwner());
		assertNull(attempt.getStartedAt());
		assertNull(attempt.getCompletedAt());
	}

	@Test
	void startingAttemptCanRunAndSucceed() {
		ExecutionAttempt attempt = newAttempt();
		assertTrue(attempt.markRunning(NOW));
		assertEquals(ExecutionAttemptStatus.RUNNING, attempt.getStatus());
		assertEquals(NOW, attempt.getStartedAt());

		assertTrue(attempt.markSucceeded(NOW.plusSeconds(5)));
		assertEquals(ExecutionAttemptStatus.SUCCEEDED, attempt.getStatus());
		assertEquals(NOW.plusSeconds(5), attempt.getCompletedAt());
	}

	@Test
	void runningAttemptCanFail() {
		ExecutionAttempt attempt = newAttempt();
		attempt.markRunning(NOW);
		assertTrue(attempt.markFailed("EXECUTOR_FAILURE", NOW.plusSeconds(5)));
		assertEquals(ExecutionAttemptStatus.FAILED, attempt.getStatus());
		assertEquals("EXECUTOR_FAILURE", attempt.getFailureCode());
	}

	@Test
	void runningAttemptCanBeAbandonedThenRecoveryRequired() {
		ExecutionAttempt attempt = newAttempt();
		attempt.markRunning(NOW);
		assertTrue(attempt.markAbandoned(NOW.plusSeconds(60)));
		assertEquals(ExecutionAttemptStatus.ABANDONED, attempt.getStatus());

		assertTrue(attempt.markRecoveryRequired("AMBIGUOUS_SIDE_EFFECT"));
		assertEquals(ExecutionAttemptStatus.RECOVERY_REQUIRED, attempt.getStatus());
		assertEquals("AMBIGUOUS_SIDE_EFFECT", attempt.getFailureCode());
	}

	@Test
	void runningAttemptCanDeclareRecoveryRequired() {
		ExecutionAttempt attempt = newAttempt();
		attempt.markRunning(NOW);
		assertTrue(attempt.markRecoveryRequired("CANNOT_REPLAY"));
		assertEquals(ExecutionAttemptStatus.RECOVERY_REQUIRED, attempt.getStatus());
	}

	@Test
	void terminalAttemptRejectsFurtherTransitions() {
		ExecutionAttempt attempt = newAttempt();
		attempt.markRunning(NOW);
		attempt.markSucceeded(NOW.plusSeconds(1));

		assertFalse(attempt.markFailed("FAILURE", NOW.plusSeconds(2)));
		assertFalse(attempt.markAbandoned(NOW.plusSeconds(2)));
		assertFalse(attempt.markRecoveryRequired("FAILURE"));
		assertEquals(ExecutionAttemptStatus.SUCCEEDED, attempt.getStatus());
	}

	@Test
	void startingAttemptCannotSkipToTerminal() {
		ExecutionAttempt attempt = newAttempt();
		assertFalse(attempt.markSucceeded(NOW));
		assertFalse(attempt.markFailed("FAILURE", NOW));
		assertFalse(attempt.markAbandoned(NOW));
		assertEquals(ExecutionAttemptStatus.STARTING, attempt.getStatus());
	}

	@Test
	void leaseCanBeAppliedTouchedAndCleared() {
		ExecutionAttempt attempt = newAttempt();
		Instant expiry = NOW.plusSeconds(60);
		attempt.applyLease(new JobLease("worker-1", 3, expiry));
		assertEquals("worker-1", attempt.getLeaseOwner());
		assertEquals(Long.valueOf(3), attempt.getLeaseToken());
		assertEquals(expiry, attempt.getLeaseExpiresAt());

		attempt.touchHeartbeat(NOW.plusSeconds(10));
		assertEquals(NOW.plusSeconds(10), attempt.getHeartbeatAt());

		attempt.clearLease();
		assertNull(attempt.getLeaseOwner());
		assertNull(attempt.getLeaseToken());
		assertNull(attempt.getLeaseExpiresAt());
		assertNull(attempt.getHeartbeatAt());
	}

	@Test
	void executionBindingAndRecoveryCount() {
		ExecutionAttempt attempt = newAttempt();
		attempt.setExecutionId("exec-1");
		assertEquals("exec-1", attempt.getExecutionId());
		assertEquals(1, attempt.incrementRecoveryCount());
		assertEquals(2, attempt.incrementRecoveryCount());
	}

	@Test
	void restoreKeepsSnapshotShape() {
		ExecutionAttempt attempt = ExecutionAttempt.restore("attempt-1", "job-1", 2, "exec-1",
			ExecutionAttemptStatus.RUNNING, "worker-1", 5L, NOW.plusSeconds(30), NOW.plusSeconds(5),
			null, 0, NOW.minusSeconds(10), NOW, null);
		assertEquals("attempt-1", attempt.getId());
		assertEquals("job-1", attempt.getJobId());
		assertEquals(2, attempt.getAttemptNo());
		assertEquals(ExecutionAttemptStatus.RUNNING, attempt.getStatus());
		assertEquals("exec-1", attempt.getExecutionId());
		assertEquals("worker-1", attempt.getLeaseOwner());
		assertEquals(Long.valueOf(5), attempt.getLeaseToken());
	}
}
