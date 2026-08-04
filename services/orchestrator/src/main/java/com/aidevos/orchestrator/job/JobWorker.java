package com.aidevos.orchestrator.job;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.execution.ExecutionCapture;
import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.persistence.LeaseableJobRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JobWorker {

	private final ExecutionEngine executionEngine;
	private final ExecutionRecordManager executionRecordManager;
	private final LeaseableJobRepository jobRepository;
	private final AuditService auditService;
	private final BlockingQueue<ExecutionJob> queue;
	private final ExecutorService workerExecutor;
	private final ScheduledExecutorService heartbeatExecutor;
	private final String workerId;
	private final Duration leaseDuration;
	private final Duration heartbeatInterval;
	private final Duration shutdownTimeout;
	private volatile ActiveLease activeLease;
	private volatile boolean running;

	private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);
	private static final long QUEUE_POLL_MILLIS = 100;

	public JobWorker(ExecutionEngine executionEngine,
			@Value("${execution.jobs.capacity:100}") int capacity) {
		this(executionEngine, new ExecutionRecordManager(), null, AuditService.noop(), capacity,
			defaultWorkerId(), defaultLeaseDuration(), (Duration) null);
	}

	public JobWorker(ExecutionEngine executionEngine, ExecutionRecordManager executionRecordManager,
			@Value("${execution.jobs.capacity:100}") int capacity) {
		this(executionEngine, executionRecordManager, null, AuditService.noop(), capacity,
			defaultWorkerId(), defaultLeaseDuration(), (Duration) null);
	}

	public JobWorker(ExecutionEngine executionEngine, ExecutionRecordManager executionRecordManager,
			LeaseableJobRepository jobRepository, AuditService auditService,
			@Value("${execution.jobs.capacity:100}") int capacity) {
		this(executionEngine, executionRecordManager, jobRepository, auditService, capacity,
			defaultWorkerId(), defaultLeaseDuration(), (Duration) null);
	}

	@Autowired
	public JobWorker(ExecutionEngine executionEngine, ExecutionRecordManager executionRecordManager,
			LeaseableJobRepository jobRepository,
			AuditService auditService,
			@Value("${execution.jobs.capacity:100}") int capacity,
			@Value("${execution.jobs.worker-id:}") String workerId,
			@Value("${execution.jobs.lease-duration:30m}") Duration leaseDuration,
			@Value("${execution.jobs.heartbeat-interval:}") String heartbeatInterval,
			@Value("${execution.jobs.shutdown-timeout:30s}") Duration shutdownTimeout) {
		this(executionEngine, executionRecordManager, jobRepository, auditService, capacity,
			workerId, leaseDuration, parseHeartbeatInterval(heartbeatInterval, leaseDuration),
			shutdownTimeout);
	}

	/**
	 * Core constructor used by Spring and tests. A null heartbeat interval is
	 * derived from the lease duration (one third, per the lease design).
	 */
	JobWorker(ExecutionEngine executionEngine, ExecutionRecordManager executionRecordManager,
			LeaseableJobRepository jobRepository, AuditService auditService, int capacity,
			String workerId, Duration leaseDuration, Duration heartbeatInterval) {
		this(executionEngine, executionRecordManager, jobRepository, auditService, capacity,
			workerId, leaseDuration, heartbeatInterval, DEFAULT_SHUTDOWN_TIMEOUT);
	}

	JobWorker(ExecutionEngine executionEngine, ExecutionRecordManager executionRecordManager,
			LeaseableJobRepository jobRepository, AuditService auditService, int capacity,
			String workerId, Duration leaseDuration, Duration heartbeatInterval,
			Duration shutdownTimeout) {
		this.executionEngine = executionEngine;
		this.executionRecordManager = executionRecordManager;
		this.jobRepository = jobRepository;
		this.auditService = auditService;
		this.queue = new ArrayBlockingQueue<>(capacity);
		this.workerExecutor = Executors.newSingleThreadExecutor(
			Thread.ofPlatform().name("execution-job-worker").factory());
		this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(
			Thread.ofPlatform().daemon().name("execution-job-heartbeat").factory());
		this.workerId = workerId == null || workerId.isBlank() ? defaultWorkerId() : workerId;
		this.leaseDuration = leaseDuration == null ? defaultLeaseDuration() : leaseDuration;
		this.heartbeatInterval = heartbeatInterval == null
			? defaultHeartbeatInterval(this.leaseDuration)
			: heartbeatInterval;
		this.shutdownTimeout = shutdownTimeout == null ? DEFAULT_SHUTDOWN_TIMEOUT : shutdownTimeout;
	}

	public boolean submit(ExecutionJob job) {
		return queue.offer(job);
	}

	@PostConstruct
	public synchronized void start() {
		if (running) {
			return;
		}
		running = true;
		workerExecutor.submit(this::run);
		heartbeatExecutor.scheduleWithFixedDelay(this::heartbeat,
			Math.max(1, heartbeatInterval.toMillis()), Math.max(1, heartbeatInterval.toMillis()),
			TimeUnit.MILLISECONDS);
	}

	private void run() {
		while (running) {
			try {
				ExecutionJob job = queue.poll(QUEUE_POLL_MILLIS, TimeUnit.MILLISECONDS);
				if (job == null) {
					continue;
				}
				if (jobRepository == null) {
					execute(job, null);
					continue;
				}
				JobStatus before = job.getStatus();
				Optional<JobLease> lease = jobRepository.claimNext(Instant.now(), workerId,
					leaseDuration);
				if (lease.isEmpty()) {
					queue.offer(job);
					Thread.sleep(50);
					continue;
				}
				beginHeartbeat(job.getId(), lease.get());
				try {
					execute(job, new LeaseContext(before, lease.get()));
				}
				finally {
					endHeartbeat();
				}
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	/**
	 * Renews the lease of the job currently being executed. Runs on a separate
	 * scheduler so a blocking executor cannot stop the heartbeat. If the renewal
	 * fails the lease has been superseded (fenced); heartbeating stops and the
	 * eventual complete() is rejected by the repository.
	 */
	private void heartbeat() {
		ActiveLease lease = activeLease;
		if (!running || jobRepository == null || lease == null) {
			return;
		}
		Instant now = Instant.now();
		if (!lease.expiresAt().isAfter(now)) {
			return;
		}
		Instant renewedExpiry = now.plus(leaseDuration);
		if (jobRepository.renewLease(lease.jobId(), lease.owner(), lease.token(),
			renewedExpiry)) {
			activeLease = new ActiveLease(lease.jobId(), lease.owner(), lease.token(),
				renewedExpiry);
		}
		else {
			activeLease = null;
		}
	}

	private void beginHeartbeat(String jobId, JobLease lease) {
		activeLease = new ActiveLease(jobId, lease.owner(), lease.token(), lease.expiresAt());
	}

	private void endHeartbeat() {
		activeLease = null;
	}

	private void execute(ExecutionJob job, LeaseContext leaseContext) {
		JobStatus before = leaseContext == null ? job.getStatus() : leaseContext.before;
		job.markRunning();
		if (leaseContext == null) {
			save(job);
		}
		if (before != job.getStatus()) {
			auditService.jobEvent(EventType.JOB_STARTED, job, before.name(), job.getStatus().name());
		}
		try {
			ExecutionCapture<ExecutionResult> capture = executionRecordManager.capture(
				() -> executionEngine.execute(job.getTaskSnapshot(), job.getId()));
			ExecutionResult result = capture.result();
			ExecutionRecord record = capture.executionRecord();
			String executionRecordId = record == null ? null : record.getId();
			if (result.isApprovalRequired()) {
				job.markWaitingApproval(result, executionRecordId);
			}
			else if (result.isSuccess()) {
				job.markSucceeded(result, executionRecordId);
			}
			else {
				job.markFailed(result, result.getMessage(), executionRecordId);
			}
		}
		catch (Throwable ex) {
			job.markFailed(null, errorMessage(ex));
		}
		finally {
			if (leaseContext != null) {
				jobRepository.complete(job.getId(), leaseContext.lease.owner(),
					leaseContext.lease.token(), job);
			}
			else {
				save(job);
			}
			EventType type = switch (job.getStatus()) {
				case SUCCESS -> EventType.JOB_SUCCEEDED;
				case FAILED -> EventType.JOB_FAILED;
				case WAITING_APPROVAL -> EventType.JOB_WAITING_APPROVAL;
				default -> null;
			};
			if (type != null) auditService.jobEvent(type, job, JobStatus.RUNNING.name(),
				job.getStatus().name());
		}
	}

	private static String defaultWorkerId() {
		return "worker-" + UUID.randomUUID();
	}

	private static Duration defaultLeaseDuration() {
		return Duration.ofMinutes(30);
	}

	private static Duration defaultHeartbeatInterval(Duration leaseDuration) {
		Duration interval = leaseDuration.dividedBy(3);
		return interval.isZero() || interval.isNegative()
			? Duration.ofSeconds(1)
			: interval;
	}

	private static Duration parseHeartbeatInterval(String value, Duration leaseDuration) {
		if (value == null || value.isBlank()) {
			return defaultHeartbeatInterval(leaseDuration);
		}
		if (value.startsWith("P") || value.startsWith("-P") || value.startsWith("+P")) {
			return Duration.parse(value);
		}
		return Duration.parse("PT" + value);
	}

	private void save(ExecutionJob job) {
		if (jobRepository != null) jobRepository.save(job);
	}

	private String errorMessage(Throwable ex) {
		return ex.getMessage() == null || ex.getMessage().isBlank()
				? ex.getClass().getName()
				: ex.getMessage();
	}

	@PreDestroy
	public synchronized void stop() {
		boolean wasRunning = running;
		running = false;
		workerExecutor.shutdown();
		if (wasRunning) {
			try {
				if (!workerExecutor.awaitTermination(shutdownTimeout.toMillis(),
					TimeUnit.MILLISECONDS)) {
					workerExecutor.shutdownNow();
					workerExecutor.awaitTermination(5, TimeUnit.SECONDS);
				}
			}
			catch (InterruptedException ex) {
				workerExecutor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
		heartbeatExecutor.shutdownNow();
	}

	private record LeaseContext(JobStatus before, JobLease lease) {
	}

	private record ActiveLease(String jobId, String owner, long token, Instant expiresAt) {
	}
}
