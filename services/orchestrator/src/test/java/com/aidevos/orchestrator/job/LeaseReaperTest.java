package com.aidevos.orchestrator.job;

import java.time.Duration;
import java.time.Instant;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LeaseReaperTest {

	private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");
	private static final String OWNER = "worker-1";

	private final JobStore jobs = new JobStore();

	@Test
	void shouldMarkStaleJobRecoveryRequiredAndClearLease() {
		job("job-1");
		jobs.claimNext(NOW, OWNER, Duration.ofSeconds(60));
		jobs.renewLease("job-1", OWNER, 1, NOW.plusSeconds(30));

		LeaseReaper reaper = new LeaseReaper(jobs, AuditService.noop(), 10);
		int reaped = reaper.reap(NOW.plusSeconds(60));

		assertEquals(1, reaped);
		ExecutionJob stored = jobs.get("job-1");
		assertEquals(JobStatus.RECOVERY_REQUIRED, stored.getStatus());
		assertEquals("LEASE_EXPIRED", stored.getLastFailureCode());
		assertEquals(1, stored.getRecoveryCount());
		assertNull(stored.getLeaseOwner());
		assertNull(stored.getLeaseExpiresAt());
	}

	@Test
	void shouldIgnoreActiveLeaseAndNonRunningJobs() {
		job("job-1");
		jobs.claimNext(NOW, OWNER, Duration.ofSeconds(60));
		job("job-2");

		LeaseReaper reaper = new LeaseReaper(jobs, AuditService.noop(), 10);
		int reaped = reaper.reap(NOW.plusSeconds(30));

		assertEquals(0, reaped);
		assertEquals(JobStatus.RUNNING, jobs.get("job-1").getStatus());
		assertEquals(OWNER, jobs.get("job-1").getLeaseOwner());
		assertEquals(JobStatus.QUEUED, jobs.get("job-2").getStatus());
	}

	@Test
	void shouldBeIdempotent() {
		job("job-1");
		jobs.claimNext(NOW, OWNER, Duration.ofSeconds(60));
		jobs.renewLease("job-1", OWNER, 1, NOW.plusSeconds(10));

		LeaseReaper reaper = new LeaseReaper(jobs, AuditService.noop(), 10);
		assertEquals(1, reaper.reap(NOW.plusSeconds(60)));
		assertEquals(0, reaper.reap(NOW.plusSeconds(60)));
		assertEquals(1, jobs.get("job-1").getRecoveryCount());
	}

	@Test
	void shouldRespectBatchLimit() {
		job("job-1");
		job("job-2");
		jobs.claimNext(NOW, OWNER, Duration.ofSeconds(60));
		jobs.claimNext(NOW, OWNER, Duration.ofSeconds(60));
		jobs.renewLease("job-1", OWNER, 1, NOW.plusSeconds(5));
		jobs.renewLease("job-2", OWNER, 1, NOW.plusSeconds(10));

		LeaseReaper reaper = new LeaseReaper(jobs, AuditService.noop(), 1);
		int reaped = reaper.reap(NOW.plusSeconds(60));

		assertEquals(1, reaped);
		assertEquals(JobStatus.RECOVERY_REQUIRED, jobs.get("job-1").getStatus());
		assertEquals(JobStatus.RUNNING, jobs.get("job-2").getStatus());
	}

	private void job(String id) {
		TaskDefinition task = new TaskDefinition();
		task.setId("task-" + id);
		jobs.save(new ExecutionJob(id, task));
	}
}
