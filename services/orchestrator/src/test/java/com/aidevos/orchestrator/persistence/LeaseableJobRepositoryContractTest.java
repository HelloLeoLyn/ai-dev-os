package com.aidevos.orchestrator.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobLease;
import com.aidevos.orchestrator.job.JobStatus;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.junit.jupiter.api.Test;

public abstract class LeaseableJobRepositoryContractTest {

	private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");
	private static final Duration LEASE_DURATION = Duration.ofSeconds(60);
	private static final String OWNER = "worker-1";

	protected abstract LeaseableJobRepository repository();

	protected ExecutionJob saveJob(String id) {
		ExecutionJob job = new ExecutionJob(id, taskDefinition("task-" + id));
		repository().save(job);
		return job;
	}

	private TaskDefinition taskDefinition(String id) {
		TaskDefinition task = new TaskDefinition();
		task.setId(id);
		task.setName(id);
		return task;
	}

	private ExecutionResult successResult() {
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(true);
		result.setMessage("done");
		return result;
	}

	private List<String> ids(List<ExecutionJob> jobs) {
		return jobs.stream().map(ExecutionJob::getId).toList();
	}

	@Test
	void claimNextGrantsLeaseAndMarksRunning() {
		saveJob("job-1");
		saveJob("job-2");

		Optional<JobLease> claimed = repository().claimNext(NOW, OWNER, LEASE_DURATION);

		assertTrue(claimed.isPresent());
		assertEquals(OWNER, claimed.get().owner());
		assertEquals(1, claimed.get().token());
		assertEquals(NOW.plus(LEASE_DURATION), claimed.get().expiresAt());

		ExecutionJob stored = repository().get("job-1");
		assertEquals(JobStatus.RUNNING, stored.getStatus());
		assertEquals(1, stored.getAttemptNo());
		assertEquals(OWNER, stored.getLeaseOwner());
		assertEquals(Long.valueOf(1), stored.getLeaseToken());
		assertEquals(NOW.plus(LEASE_DURATION), stored.getLeaseExpiresAt());
		assertEquals(1, stored.getVersion());
	}

	@Test
	void claimNextSkipsFutureRetryWaitAndClaimsDueRetryWait() {
		saveJob("job-1");
		repository().claimNext(NOW, OWNER, LEASE_DURATION);
		repository().retryWait("job-1", "EXECUTOR_FAILURE", NOW.plusSeconds(30));

		assertTrue(repository().claimNext(NOW, OWNER, LEASE_DURATION).isEmpty());
		assertEquals(JobStatus.RETRY_WAIT, repository().get("job-1").getStatus());

		saveJob("job-2");
		repository().claimNext(NOW, OWNER, LEASE_DURATION);
		repository().retryWait("job-2", "EXECUTOR_FAILURE", NOW.minusSeconds(1));

		Optional<JobLease> claimed = repository().claimNext(NOW, OWNER, LEASE_DURATION);
		assertTrue(claimed.isPresent());
		assertEquals(2, claimed.get().token());
		assertEquals(JobStatus.RUNNING, repository().get("job-2").getStatus());
		assertEquals(2, repository().get("job-2").getAttemptNo());
	}

	@Test
	void claimNextReturnsEmptyWhenNothingIsDue() {
		assertTrue(repository().claimNext(NOW, OWNER, LEASE_DURATION).isEmpty());

		saveJob("job-1");
		repository().claimNext(NOW, OWNER, LEASE_DURATION);
		repository().retryWait("job-1", "BACKOFF", NOW.plusSeconds(60));

		assertTrue(repository().claimNext(NOW, OWNER, LEASE_DURATION).isEmpty());
		assertEquals(JobStatus.RETRY_WAIT, repository().get("job-1").getStatus());
	}

	@Test
	void renewLeaseRequiresValidOwnerAndToken() {
		saveJob("job-1");
		JobLease lease = repository().claimNext(NOW, OWNER, LEASE_DURATION).orElseThrow();
		Instant renewed = NOW.plusSeconds(120);

		assertTrue(repository().renewLease("job-1", OWNER, lease.token(), renewed));
		assertEquals(renewed, repository().get("job-1").getLeaseExpiresAt());

		assertFalse(repository().renewLease("job-1", OWNER, lease.token() + 1,
			renewed.plusSeconds(10)));
		assertFalse(repository().renewLease("job-1", "worker-2", lease.token(),
			renewed.plusSeconds(10)));
		assertEquals(renewed, repository().get("job-1").getLeaseExpiresAt());
	}

	@Test
	void releaseLeaseRequeuesWithMonotonicToken() {
		saveJob("job-1");
		JobLease first = repository().claimNext(NOW, OWNER, LEASE_DURATION).orElseThrow();

		assertTrue(repository().releaseLease("job-1", OWNER, first.token(), JobStatus.QUEUED));
		ExecutionJob released = repository().get("job-1");
		assertEquals(JobStatus.QUEUED, released.getStatus());
		assertNull(released.getLeaseOwner());
		assertNull(released.getLeaseExpiresAt());

		JobLease second = repository().claimNext(NOW, "worker-2", LEASE_DURATION).orElseThrow();
		assertEquals(2, second.token());
		assertEquals("worker-2", repository().get("job-1").getLeaseOwner());
	}

	@Test
	void releaseLeaseSupportsCancelledAndRejectsInvalidStatus() {
		saveJob("job-1");
		JobLease lease = repository().claimNext(NOW, OWNER, LEASE_DURATION).orElseThrow();

		assertFalse(repository().releaseLease("job-1", OWNER, lease.token(), JobStatus.SUCCESS));
		assertFalse(repository().releaseLease("job-1", OWNER, lease.token(), JobStatus.RUNNING));
		assertFalse(repository().releaseLease("job-1", OWNER, lease.token(),
			JobStatus.RECOVERY_REQUIRED));
		assertEquals(JobStatus.RUNNING, repository().get("job-1").getStatus());

		assertTrue(repository().releaseLease("job-1", OWNER, lease.token(), JobStatus.CANCELLED));
		assertEquals(JobStatus.CANCELLED, repository().get("job-1").getStatus());
	}

	@Test
	void completeWritesTerminalStateAndClearsLease() {
		ExecutionJob job = saveJob("job-1");
		JobLease lease = repository().claimNext(NOW, OWNER, LEASE_DURATION).orElseThrow();
		job.markSucceeded(successResult());

		assertTrue(repository().complete("job-1", OWNER, lease.token(), job));

		ExecutionJob stored = repository().get("job-1");
		assertEquals(JobStatus.SUCCESS, stored.getStatus());
		assertNotNull(stored.getResult());
		assertNull(stored.getLeaseOwner());
		assertNull(stored.getLeaseExpiresAt());
		assertEquals(2, stored.getVersion());

		assertFalse(repository().complete("job-1", OWNER, lease.token(), job));
	}

	@Test
	void completeRejectsStaleWorkerAfterLeaseIsSuperseded() {
		ExecutionJob job = saveJob("job-1");
		JobLease first = repository().claimNext(NOW, OWNER, LEASE_DURATION).orElseThrow();
		repository().releaseLease("job-1", OWNER, first.token(), JobStatus.QUEUED);
		repository().claimNext(NOW, "worker-2", LEASE_DURATION);

		ExecutionJob staleSnapshot = new ExecutionJob("job-1", taskDefinition("task-job-1"));
		staleSnapshot.markSucceeded(successResult());

		assertFalse(repository().complete("job-1", OWNER, first.token(), staleSnapshot));
		assertFalse(repository().renewLease("job-1", OWNER, first.token(), NOW.plusSeconds(60)));
		assertEquals("worker-2", repository().get("job-1").getLeaseOwner());
	}

	@Test
	void findStaleReturnsExpiredLeaseJobsRespectingLimit() {
		saveJob("job-1");
		saveJob("job-2");
		saveJob("job-3");
		repository().claimNext(NOW, OWNER, LEASE_DURATION);
		repository().claimNext(NOW, OWNER, LEASE_DURATION);
		repository().claimNext(NOW, OWNER, LEASE_DURATION);

		repository().renewLease("job-1", OWNER, 1, NOW.minusSeconds(10));
		repository().renewLease("job-2", OWNER, 1, NOW.minusSeconds(5));
		repository().cancel("job-3");

		assertEquals(List.of("job-1", "job-2"), ids(repository().findStale(NOW, 10)));
		assertEquals(List.of("job-1"), ids(repository().findStale(NOW, 1)));
	}

	@Test
	void markRecoveryRequiredTransitionsAndClearsLease() {
		saveJob("job-1");
		repository().claimNext(NOW, OWNER, LEASE_DURATION);

		assertTrue(repository().markRecoveryRequired("job-1", "AMBIGUOUS_SIDE_EFFECT"));
		ExecutionJob stored = repository().get("job-1");
		assertEquals(JobStatus.RECOVERY_REQUIRED, stored.getStatus());
		assertEquals("AMBIGUOUS_SIDE_EFFECT", stored.getLastFailureCode());
		assertEquals(1, stored.getRecoveryCount());
		assertNull(stored.getLeaseOwner());

		assertFalse(repository().markRecoveryRequired("job-1", "AGAIN"));
	}

	@Test
	void retryWaitSetsAvailableAtAndClearsLease() {
		saveJob("job-1");
		repository().claimNext(NOW, OWNER, LEASE_DURATION);
		Instant availableAt = NOW.plusSeconds(30);

		assertTrue(repository().retryWait("job-1", "EXECUTOR_FAILURE", availableAt));
		ExecutionJob stored = repository().get("job-1");
		assertEquals(JobStatus.RETRY_WAIT, stored.getStatus());
		assertEquals("EXECUTOR_FAILURE", stored.getLastFailureCode());
		assertEquals(availableAt, stored.getAvailableAt());
		assertNull(stored.getLeaseOwner());

		assertFalse(repository().retryWait("job-1", "AGAIN", availableAt));
	}

	@Test
	void cancelTransitionsToCancelled() {
		saveJob("job-1");
		repository().claimNext(NOW, OWNER, LEASE_DURATION);

		assertTrue(repository().cancel("job-1"));
		ExecutionJob stored = repository().get("job-1");
		assertEquals(JobStatus.CANCELLED, stored.getStatus());
		assertNull(stored.getLeaseOwner());
		assertFalse(repository().cancel("job-2"));
	}

	@Test
	void operationsOnUnknownJobAreRejected() {
		ExecutionJob unknown = new ExecutionJob("missing", taskDefinition("task-missing"));
		assertFalse(repository().renewLease("missing", OWNER, 1, NOW.plusSeconds(10)));
		assertFalse(repository().releaseLease("missing", OWNER, 1, JobStatus.QUEUED));
		assertFalse(repository().complete("missing", OWNER, 1, unknown));
		assertFalse(repository().markRecoveryRequired("missing", "FAILURE"));
		assertFalse(repository().retryWait("missing", "FAILURE", NOW.plusSeconds(10)));
		assertFalse(repository().cancel("missing"));
		assertTrue(repository().findStale(NOW, 10).isEmpty());
		assertTrue(repository().claimNext(NOW, OWNER, LEASE_DURATION).isEmpty());
	}
}
