package com.aidevos.orchestrator.job;

import java.time.Duration;
import java.time.Instant;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobWorkerTest {

	private JobWorker worker;

	@AfterEach
	void stopWorker() {
		if (worker != null) {
			worker.stop();
		}
	}

	@Test
	void shouldCompleteSuccessfulExecution() throws Exception {
		ExecutionEngine engine = mock(ExecutionEngine.class);
		ExecutionRecordManager recordManager = new ExecutionRecordManager();
		ExecutionResult result = result(true, "completed");
		when(engine.execute(any(TaskDefinition.class), anyString())).thenAnswer(invocation -> {
			ExecutionRecord record = new ExecutionRecord();
			record.setId("record-1");
			recordManager.save(record);
			return result;
		});
		ExecutionJob job = execute(engine, recordManager);

		awaitStatus(job, JobStatus.SUCCESS);

		assertEquals(result, job.getResult());
		assertEquals("record-1", job.getExecutionRecordId());
		assertEquals("completed", job.getResultSummary());
		assertNotNull(job.getCreatedAt());
		assertNotNull(job.getStartedAt());
		assertNotNull(job.getCompletedAt());
		assertFalse(job.getStartedAt().isBefore(job.getCreatedAt()));
		assertFalse(job.getCompletedAt().isBefore(job.getStartedAt()));
	}

	@Test
	void shouldCompleteFailedExecution() throws Exception {
		ExecutionEngine engine = mock(ExecutionEngine.class);
		when(engine.execute(any(TaskDefinition.class), anyString())).thenReturn(result(false, "agent failed"));
		ExecutionJob job = execute(engine);

		awaitStatus(job, JobStatus.FAILED);

		assertEquals("agent failed", job.getError());
		assertEquals("agent failed", job.getErrorMessage());
	}

	@Test
	void shouldFailJobWhenEngineThrows() throws Exception {
		ExecutionEngine engine = mock(ExecutionEngine.class);
		when(engine.execute(any(TaskDefinition.class), anyString())).thenThrow(new AssertionError("engine crashed"));
		ExecutionJob job = execute(engine);

		awaitStatus(job, JobStatus.FAILED);

		assertEquals("engine crashed", job.getError());
	}

	@Test
	void shouldWaitForApprovalWithoutFailingJob() throws Exception {
		ExecutionEngine engine = mock(ExecutionEngine.class);
		ExecutionResult result = result(false, "APPROVAL_REQUIRED");
		result.setApprovalRequired(true);
		result.setApprovalId("approval-1");
		when(engine.execute(any(TaskDefinition.class), anyString())).thenReturn(result);
		ExecutionJob job = execute(engine);

		awaitStatus(job, JobStatus.WAITING_APPROVAL);

		assertEquals("approval-1", job.getApprovalId());
		assertEquals(result, job.getResult());
	}

	@Test
	void shouldClaimAndCompleteJobThroughLeaseableRepository() throws Exception {
		ExecutionEngine engine = mock(ExecutionEngine.class);
		ExecutionResult result = result(true, "claimed and done");
		when(engine.execute(any(TaskDefinition.class), anyString())).thenReturn(result);
		JobStore jobs = new JobStore();
		worker = new JobWorker(engine, new ExecutionRecordManager(), jobs, AuditService.noop(), 2);
		TaskDefinition task = new TaskDefinition();
		task.setId("task-1");
		ExecutionJob job = new ExecutionJob("job-1", task);
		jobs.save(job);
		worker.submit(job);
		worker.start();

		awaitStatus(job, JobStatus.SUCCESS);

		ExecutionJob stored = jobs.get("job-1");
		assertEquals(JobStatus.SUCCESS, stored.getStatus());
		assertEquals(result, stored.getResult());
		assertEquals("claimed and done", stored.getResultSummary());
		assertEquals(1, stored.getAttemptNo());
		assertNull(stored.getLeaseOwner());
		assertNull(stored.getLeaseExpiresAt());
	}

	@Test
	void shouldClaimDueRetryWaitJobThroughLeaseableRepository() throws Exception {
		ExecutionEngine engine = mock(ExecutionEngine.class);
		when(engine.execute(any(TaskDefinition.class), anyString())).thenReturn(result(true, "retried"));
		JobStore jobs = new JobStore();
		worker = new JobWorker(engine, new ExecutionRecordManager(), jobs, AuditService.noop(), 2);
		TaskDefinition task = new TaskDefinition();
		task.setId("task-1");
		ExecutionJob job = new ExecutionJob("job-1", task);
		job.markRunning();
		job.markRetryWait("EXECUTOR_FAILURE", Instant.now().minusSeconds(1));
		jobs.save(job);
		worker.submit(job);
		worker.start();

		awaitStatus(job, JobStatus.SUCCESS);

		ExecutionJob stored = jobs.get("job-1");
		assertEquals(JobStatus.SUCCESS, stored.getStatus());
		assertEquals(1, stored.getAttemptNo());
	}

	@Test
	void shouldNotExecuteFutureRetryWaitJob() throws Exception {
		ExecutionEngine engine = mock(ExecutionEngine.class);
		JobStore jobs = new JobStore();
		worker = new JobWorker(engine, new ExecutionRecordManager(), jobs, AuditService.noop(), 2);
		TaskDefinition task = new TaskDefinition();
		task.setId("task-1");
		ExecutionJob job = new ExecutionJob("job-1", task);
		job.markRunning();
		job.markRetryWait("BACKOFF", Instant.now().plusSeconds(30));
		jobs.save(job);
		worker.submit(job);
		worker.start();

		Thread.sleep(150);

		assertEquals(JobStatus.RETRY_WAIT, job.getStatus());
		verify(engine, never()).execute(any(TaskDefinition.class), anyString());
	}

	private ExecutionJob execute(ExecutionEngine engine) {
		return execute(engine, new ExecutionRecordManager());
	}

	private ExecutionJob execute(ExecutionEngine engine, ExecutionRecordManager recordManager) {
		worker = new JobWorker(engine, recordManager, 1);
		TaskDefinition task = new TaskDefinition();
		task.setId("task-1");
		ExecutionJob job = new ExecutionJob("job-1", task);
		worker.submit(job);
		worker.start();
		return job;
	}

	private void awaitStatus(ExecutionJob job, JobStatus expected) throws Exception {
		long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
		while (job.getStatus() != expected && System.nanoTime() < deadline) {
			Thread.sleep(10);
		}
		assertEquals(expected, job.getStatus());
	}

	private ExecutionResult result(boolean success, String message) {
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(success);
		result.setMessage(message);
		return result;
	}
}
