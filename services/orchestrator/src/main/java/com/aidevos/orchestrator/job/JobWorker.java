package com.aidevos.orchestrator.job;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
	private final String workerId;
	private final Duration leaseDuration;
	private volatile boolean running;

	public JobWorker(ExecutionEngine executionEngine,
			@Value("${execution.jobs.capacity:100}") int capacity) {
		this(executionEngine, new ExecutionRecordManager(), null, AuditService.noop(), capacity,
			defaultWorkerId(), defaultLeaseDuration());
	}

	public JobWorker(ExecutionEngine executionEngine, ExecutionRecordManager executionRecordManager,
			@Value("${execution.jobs.capacity:100}") int capacity) {
		this(executionEngine, executionRecordManager, null, AuditService.noop(), capacity,
			defaultWorkerId(), defaultLeaseDuration());
	}

	public JobWorker(ExecutionEngine executionEngine, ExecutionRecordManager executionRecordManager,
			LeaseableJobRepository jobRepository, AuditService auditService,
			@Value("${execution.jobs.capacity:100}") int capacity) {
		this(executionEngine, executionRecordManager, jobRepository, auditService, capacity,
			defaultWorkerId(), defaultLeaseDuration());
	}

	@Autowired
	public JobWorker(ExecutionEngine executionEngine, ExecutionRecordManager executionRecordManager,
			LeaseableJobRepository jobRepository,
			AuditService auditService,
			@Value("${execution.jobs.capacity:100}") int capacity,
			@Value("${execution.jobs.worker-id:}") String workerId,
			@Value("${execution.jobs.lease-duration:30m}") Duration leaseDuration) {
		this.executionEngine = executionEngine;
		this.executionRecordManager = executionRecordManager;
		this.jobRepository = jobRepository;
		this.auditService = auditService;
		this.queue = new ArrayBlockingQueue<>(capacity);
		this.workerExecutor = Executors.newSingleThreadExecutor(
			Thread.ofPlatform().name("execution-job-worker").factory());
		this.workerId = workerId == null || workerId.isBlank() ? defaultWorkerId() : workerId;
		this.leaseDuration = leaseDuration == null ? defaultLeaseDuration() : leaseDuration;
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
	}

	private void run() {
		while (running) {
			try {
				ExecutionJob job = queue.take();
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
				execute(job, new LeaseContext(before, lease.get()));
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
		}
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
		running = false;
		workerExecutor.shutdownNow();
	}

	private record LeaseContext(JobStatus before, JobLease lease) {
	}
}
