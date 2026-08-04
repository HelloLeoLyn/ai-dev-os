package com.aidevos.orchestrator.job;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.persistence.LeaseableJobRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Periodically reclaims jobs whose lease has expired while still RUNNING.
 * The basic 8-B behavior is conservative: an expired lease transitions to
 * RECOVERY_REQUIRED so it is never re-executed without an explicit recovery
 * decision. Automatic recovery, retry and backoff are out of scope here.
 */
@Component
public class LeaseReaper {

	static final String LEASE_EXPIRED = "LEASE_EXPIRED";

	private final LeaseableJobRepository jobRepository;
	private final AuditService auditService;
	private final int batchSize;
	private final Duration interval;
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
		Thread.ofPlatform().daemon().name("lease-reaper").factory());
	private volatile boolean running;

	public LeaseReaper(LeaseableJobRepository jobRepository,
			@Value("${execution.jobs.reaper-batch-size:100}") int batchSize) {
		this(jobRepository, AuditService.noop(), batchSize);
	}

	LeaseReaper(LeaseableJobRepository jobRepository, AuditService auditService, int batchSize) {
		this(jobRepository, auditService, batchSize, Duration.ofSeconds(30));
	}

	@Autowired
	public LeaseReaper(LeaseableJobRepository jobRepository, AuditService auditService,
			@Value("${execution.jobs.reaper-batch-size:100}") int batchSize,
			@Value("${execution.jobs.reaper-interval:30s}") Duration interval) {
		this.jobRepository = jobRepository;
		this.auditService = auditService == null ? AuditService.noop() : auditService;
		this.batchSize = batchSize;
		this.interval = interval == null ? Duration.ofSeconds(30) : interval;
	}

	@PostConstruct
	public synchronized void start() {
		if (running) {
			return;
		}
		running = true;
		scheduler.scheduleWithFixedDelay(this::safeReap, interval.toMillis(),
			interval.toMillis(), TimeUnit.MILLISECONDS);
	}

	private void safeReap() {
		if (!running) {
			return;
		}
		try {
			reap(Instant.now());
		}
		catch (RuntimeException exception) {
			// A failed sweep must not kill the scheduler; retried on the next tick.
		}
	}

	public int reap(Instant now) {
		int reaped = 0;
		for (ExecutionJob job : jobRepository.findStale(now, batchSize)) {
			if (jobRepository.markRecoveryRequired(job.getId(), LEASE_EXPIRED)) {
				reaped++;
				auditService.jobEvent(EventType.JOB_RECOVERY_REQUIRED, job,
					JobStatus.RUNNING.name(), JobStatus.RECOVERY_REQUIRED.name());
			}
		}
		return reaped;
	}

	@PreDestroy
	public synchronized void stop() {
		running = false;
		scheduler.shutdownNow();
	}
}
