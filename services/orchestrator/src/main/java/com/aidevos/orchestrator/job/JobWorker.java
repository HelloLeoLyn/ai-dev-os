package com.aidevos.orchestrator.job;

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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JobWorker {

	private final ExecutionEngine executionEngine;
	private final ExecutionRecordManager executionRecordManager;
	private final JobRepository jobRepository;
	private final AuditService auditService;
	private final BlockingQueue<ExecutionJob> queue;
	private final ExecutorService workerExecutor;
	private volatile boolean running;

	public JobWorker(ExecutionEngine executionEngine,
			@Value("${execution.jobs.capacity:100}") int capacity) {
		this(executionEngine, new ExecutionRecordManager(), null, AuditService.noop(), capacity);
	}

	public JobWorker(ExecutionEngine executionEngine, ExecutionRecordManager executionRecordManager,
			@Value("${execution.jobs.capacity:100}") int capacity) {
		this(executionEngine, executionRecordManager, null, AuditService.noop(), capacity);
	}

	@Autowired
	public JobWorker(ExecutionEngine executionEngine, ExecutionRecordManager executionRecordManager,
			JobRepository jobRepository,
			AuditService auditService,
			@Value("${execution.jobs.capacity:100}") int capacity) {
		this.executionEngine = executionEngine;
		this.executionRecordManager = executionRecordManager;
		this.jobRepository = jobRepository;
		this.auditService = auditService;
		this.queue = new ArrayBlockingQueue<>(capacity);
		this.workerExecutor = Executors.newSingleThreadExecutor(
			Thread.ofPlatform().name("execution-job-worker").factory());
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
				execute(job);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private void execute(ExecutionJob job) {
		JobStatus before = job.getStatus();
		job.markRunning();
		save(job);
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
			save(job);
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
}
