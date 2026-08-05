package com.aidevos.orchestrator.persistence.postgresql;

import java.time.Duration;
import java.time.Instant;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobLease;
import com.aidevos.orchestrator.job.JobStatus;
import com.aidevos.orchestrator.job.LeaseReaper;
import com.aidevos.orchestrator.model.TaskDefinition;
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

/**
 * Phase 8-F kill -9 recovery validation. A worker that claims a job and then
 * dies without releasing the lease must be fenced: after the lease expires the
 * reaper marks the job RECOVERY_REQUIRED and the stale worker can no longer
 * renew or complete it.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresWorkerFailureRecoveryTest {

	private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");
	private static final Duration LEASE = Duration.ofSeconds(60);

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	private PostgresLeaseableJobRepository repository;

	@BeforeEach
	void setUp() {
		PGSimpleDataSource dataSource = new PGSimpleDataSource();
		dataSource.setUrl(POSTGRES.getJdbcUrl());
		dataSource.setUser(POSTGRES.getUsername());
		dataSource.setPassword(POSTGRES.getPassword());
		new PostgresDocumentStore(dataSource, new ObjectMapper());
		repository = new PostgresLeaseableJobRepository(dataSource, new ObjectMapper());
	}

	@Test
	void killedWorkerLeaseIsReapedToRecoveryRequiredAndStaleWritesAreFenced() {
		TaskDefinition task = new TaskDefinition();
		task.setId("task-1");
		repository.save(new ExecutionJob("job-1", task));

		// Worker claims the job and starts executing; then it is killed with
		// kill -9: no release, no complete, no further heartbeat.
		JobLease lease = repository.claimNext(NOW, "worker-1", LEASE).orElseThrow();
		assertEquals(1, lease.token());
		assertEquals(JobStatus.RUNNING, repository.get("job-1").getStatus());
		assertTrue(repository.renewLease("job-1", "worker-1", 1, NOW.plusSeconds(30)));

		// Lease expires; the periodic reaper sweep transitions the job.
		LeaseReaper reaper = new LeaseReaper(repository, AuditService.noop(), 100,
			Duration.ofSeconds(30));
		int reaped = reaper.reap(NOW.plusSeconds(61));
		assertEquals(1, reaped);

		ExecutionJob recovered = repository.get("job-1");
		assertEquals(JobStatus.RECOVERY_REQUIRED, recovered.getStatus());
		assertEquals("LEASE_EXPIRED", recovered.getLastFailureCode());
		assertEquals(1, recovered.getRecoveryCount());
		assertNull(recovered.getLeaseOwner());
		assertNull(recovered.getLeaseExpiresAt());

		// The stale worker is fenced: its renewal and completion are rejected.
		assertFalse(repository.renewLease("job-1", "worker-1", 1, NOW.plusSeconds(120)));
		assertFalse(repository.complete("job-1", "worker-1", 1, succeededSnapshot("job-1")));
		assertEquals(JobStatus.RECOVERY_REQUIRED, repository.get("job-1").getStatus());

		// RECOVERY_REQUIRED is never auto-claimed by another worker.
		assertTrue(repository.claimNext(NOW.plusSeconds(61), "worker-2", LEASE).isEmpty());
	}

	@Test
	void expiredLeaseJobIsNotReapedTwice() {
		TaskDefinition task = new TaskDefinition();
		task.setId("job-1");
		repository.save(new ExecutionJob("job-1", task));
		repository.claimNext(NOW, "worker-1", LEASE);
		LeaseReaper reaper = new LeaseReaper(repository, AuditService.noop(), 100,
			Duration.ofSeconds(30));

		assertEquals(1, reaper.reap(NOW.plusSeconds(61)));
		assertEquals(0, reaper.reap(NOW.plusSeconds(120)));
		assertEquals(1, repository.get("job-1").getRecoveryCount());
	}

	private ExecutionJob succeededSnapshot(String id) {
		TaskDefinition task = new TaskDefinition();
		task.setId("task-" + id);
		ExecutionJob job = new ExecutionJob(id, task);
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(true);
		result.setMessage("stale success");
		job.markSucceeded(result);
		return job;
	}
}
