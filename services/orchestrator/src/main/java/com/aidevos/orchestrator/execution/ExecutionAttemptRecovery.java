package com.aidevos.orchestrator.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Detects stale executions and marks them for recovery. Conservative by
 * design: an attempt whose lease has expired while still RUNNING is abandoned
 * and then flagged RECOVERY_REQUIRED so it is never re-run without an explicit
 * recovery decision. Automatic retry and re-execution are out of scope here.
 */
@Component
public class ExecutionAttemptRecovery {

	public static final String STALE_EXECUTION = "STALE_EXECUTION";

	private final ExecutionAttemptRepository attemptRepository;
	private final Duration interval;
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
		Thread.ofPlatform().daemon().name("execution-attempt-recovery").factory());
	private volatile boolean running;

	public ExecutionAttemptRecovery(ExecutionAttemptRepository attemptRepository) {
		this(attemptRepository, Duration.ofSeconds(30));
	}

	@Autowired
	public ExecutionAttemptRecovery(ExecutionAttemptRepository attemptRepository,
			@Value("${execution.jobs.attempt-recovery-interval:30s}") Duration interval) {
		this.attemptRepository = attemptRepository;
		this.interval = interval == null ? Duration.ofSeconds(30) : interval;
	}

	@PostConstruct
	public synchronized void start() {
		if (running) {
			return;
		}
		running = true;
		scheduler.scheduleWithFixedDelay(this::safeRecover, interval.toMillis(),
			interval.toMillis(), TimeUnit.MILLISECONDS);
	}

	private void safeRecover() {
		if (!running) {
			return;
		}
		try {
			recoverStale(Instant.now());
		}
		catch (RuntimeException exception) {
			// A failed sweep must not kill the scheduler; retried on the next tick.
		}
	}

	public int recoverStale(Instant now) {
		int recovered = 0;
		for (ExecutionAttempt attempt : attemptRepository.findAbandoned(now)) {
			if (attempt.markAbandoned(now)) {
				attemptRepository.save(attempt);
				if (attempt.markRecoveryRequired(STALE_EXECUTION)) {
					attempt.clearLease();
					attemptRepository.save(attempt);
					recovered++;
				}
			}
		}
		return recovered;
	}

	@PreDestroy
	public synchronized void stop() {
		running = false;
		scheduler.shutdownNow();
	}
}
