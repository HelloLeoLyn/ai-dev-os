package com.aidevos.orchestrator.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.junit.jupiter.api.Test;

class JobStateMachineTest {

	private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

	private ExecutionJob newJob() {
		TaskDefinition task = new TaskDefinition();
		task.setId("task-1");
		task.setName("task-1");
		return new ExecutionJob("job-1", task);
	}

	private ExecutionJob succeededJob() {
		ExecutionJob job = newJob();
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(true);
		job.markSucceeded(result);
		return job;
	}

	@Test
	void newJobDefaultsToQueuedWithControlDefaults() {
		ExecutionJob job = newJob();
		assertEquals(JobStatus.QUEUED, job.getStatus());
		assertEquals(0, job.getAttemptNo());
		assertEquals(1, job.getMaxAttempts());
		assertEquals(0, job.getPriority());
		assertEquals(0, job.getVersion());
		assertEquals(0, job.getRecoveryCount());
		assertEquals(ExecutionJob.RecoveryPolicy.MANUAL, job.getRecoveryPolicy());
		assertNull(job.getAvailableAt());
		assertNull(job.getLeaseOwner());
		assertNull(job.getLeaseToken());
		assertNull(job.getLeaseExpiresAt());
		assertNull(job.getHeartbeatAt());
		assertNull(job.getLastFailureCode());
	}

	@Test
	void queuedJobCanRun() {
		ExecutionJob job = newJob();
		job.markRunning();
		assertEquals(JobStatus.RUNNING, job.getStatus());
		assertNotNull(job.getStartedAt());
	}

	@Test
	void runningJobCanEnterRetryWaitAndResume() {
		ExecutionJob job = newJob();
		job.markRunning();
		Instant availableAt = NOW.plusSeconds(30);

		assertTrue(job.markRetryWait("LEASE_EXPIRED", availableAt));
		assertEquals(JobStatus.RETRY_WAIT, job.getStatus());
		assertEquals("LEASE_EXPIRED", job.getLastFailureCode());
		assertEquals(availableAt, job.getAvailableAt());

		job.markRunning();
		assertEquals(JobStatus.RUNNING, job.getStatus());
	}

	@Test
	void runningJobCanEnterRecoveryRequired() {
		ExecutionJob job = newJob();
		job.markRunning();
		assertTrue(job.markRecoveryRequired("AMBIGUOUS_SIDE_EFFECT"));
		assertEquals(JobStatus.RECOVERY_REQUIRED, job.getStatus());
		assertEquals("AMBIGUOUS_SIDE_EFFECT", job.getLastFailureCode());
	}

	@Test
	void terminalJobCannotEnterRetryWaitOrRecovery() {
		ExecutionJob succeeded = succeededJob();
		assertFalse(succeeded.markRetryWait("FAILURE", NOW.plusSeconds(30)));
		assertFalse(succeeded.markRecoveryRequired("FAILURE"));
		assertEquals(JobStatus.SUCCESS, succeeded.getStatus());
	}

	@Test
	void queuedJobCannotEnterRetryWaitOrRecovery() {
		ExecutionJob job = newJob();
		assertFalse(job.markRetryWait("FAILURE", NOW.plusSeconds(30)));
		assertFalse(job.markRecoveryRequired("FAILURE"));
		assertEquals(JobStatus.QUEUED, job.getStatus());
	}

	@Test
	void activeJobCanBeCancelledButTerminalJobCannot() {
		ExecutionJob active = newJob();
		active.markRunning();
		assertTrue(active.markCancelled());
		assertEquals(JobStatus.CANCELLED, active.getStatus());
		assertNotNull(active.getCompletedAt());

		ExecutionJob done = succeededJob();
		assertFalse(done.markCancelled());
		assertEquals(JobStatus.SUCCESS, done.getStatus());
	}

	@Test
	void leaseCanBeAppliedTouchedAndCleared() {
		ExecutionJob job = newJob();
		Instant expiry = NOW.plusSeconds(60);

		job.applyLease(new JobLease("worker-1", 7, expiry));
		assertEquals("worker-1", job.getLeaseOwner());
		assertEquals(Long.valueOf(7), job.getLeaseToken());
		assertEquals(expiry, job.getLeaseExpiresAt());

		job.touchHeartbeat(NOW.plusSeconds(10));
		assertEquals(NOW.plusSeconds(10), job.getHeartbeatAt());

		job.clearLease();
		assertNull(job.getLeaseOwner());
		assertEquals(Long.valueOf(7), job.getLeaseToken());
		assertNull(job.getLeaseExpiresAt());
		assertNull(job.getHeartbeatAt());
	}

	@Test
	void versionAttemptAndRecoveryCountersIncrement() {
		ExecutionJob job = newJob();
		assertEquals(1, job.bumpVersion());
		assertEquals(2, job.bumpVersion());
		assertEquals(1, job.nextAttemptNo());
		assertEquals(2, job.nextAttemptNo());
		assertEquals(1, job.incrementRecoveryCount());
		assertEquals(2, job.incrementRecoveryCount());
	}

	@Test
	void restoreKeepsPhase7ShapeWithControlDefaults() {
		ExecutionJob job = ExecutionJob.restore("job-1", task("task-1"), NOW, JobStatus.QUEUED,
			null, null, null, null, null, null, null);
		assertEquals("job-1", job.getId());
		assertEquals("task-1", job.getTaskId());
		assertEquals(JobStatus.QUEUED, job.getStatus());
		assertEquals(0, job.getAttemptNo());
		assertEquals(1, job.getMaxAttempts());
		assertEquals(ExecutionJob.RecoveryPolicy.MANUAL, job.getRecoveryPolicy());
	}

	@Test
	void maxAttemptsPriorityAndRecoveryPolicyCanBeConfigured() {
		ExecutionJob job = newJob();
		job.setMaxAttempts(3);
		job.setPriority(5);
		job.setRecoveryPolicy(ExecutionJob.RecoveryPolicy.REQUEUE);
		assertEquals(3, job.getMaxAttempts());
		assertEquals(5, job.getPriority());
		assertEquals(ExecutionJob.RecoveryPolicy.REQUEUE, job.getRecoveryPolicy());

		job.setRecoveryPolicy(null);
		assertEquals(ExecutionJob.RecoveryPolicy.MANUAL, job.getRecoveryPolicy());
	}

	private TaskDefinition task(String id) {
		TaskDefinition task = new TaskDefinition();
		task.setId(id);
		task.setName(id);
		return task;
	}
}
