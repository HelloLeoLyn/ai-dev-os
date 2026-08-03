package com.aidevos.orchestrator.job;

import com.aidevos.orchestrator.audit.*;
import com.aidevos.orchestrator.execution.*;
import com.aidevos.orchestrator.model.TaskDefinition;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobAuditTest {
	private JobWorker worker;

	@AfterEach void stop() { if (worker != null) worker.stop(); }

	@Test
	void recordsSubmissionStartAndSuccessEvents() throws Exception {
		InMemoryAuditRepository events = new InMemoryAuditRepository();
		AuditService audit = new AuditService(events);
		ExecutionEngine engine = mock(ExecutionEngine.class);
		ExecutionResult result = new ExecutionResult(); result.setSuccess(true);
		when(engine.execute(any(TaskDefinition.class), anyString())).thenReturn(result);
		JobStore jobs = new JobStore();
		worker = new JobWorker(engine, new ExecutionRecordManager(), jobs, audit, 2);
		JobService service = new JobService(jobs, worker, audit);
		worker.start();

		JobSubmissionResponse submission = service.submit(task());
		ExecutionJob job = service.get(submission.jobId());
		await(job, JobStatus.SUCCESS);

		assertEquals(java.util.List.of(EventType.JOB_SUBMITTED, EventType.JOB_STARTED,
			EventType.JOB_SUCCEEDED), events.query(EventQuery.all()).stream()
			.map(EventRecord::type).toList());
	}

	@Test
	void auditFailureDoesNotAffectExecution() throws Exception {
		AuditRepository broken = new AuditRepository() {
			public EventRecord append(EventRecord event) { throw new IllegalStateException("down"); }
			public EventRecord get(String id) { return null; }
			public java.util.List<EventRecord> query(EventQuery query) { return java.util.List.of(); }
		};
		JobStore jobs = new JobStore();
		AuditService audit = new AuditService(broken);
		ExecutionEngine engine = mock(ExecutionEngine.class);
		ExecutionResult result = new ExecutionResult(); result.setSuccess(true);
		when(engine.execute(any(TaskDefinition.class), anyString())).thenReturn(result);
		worker = new JobWorker(engine, new ExecutionRecordManager(), jobs, audit, 2);
		JobService service = new JobService(jobs, worker, audit);
		worker.start();

		JobSubmissionResponse response = assertDoesNotThrow(() -> service.submit(task()));

		await(jobs.get(response.jobId()), JobStatus.SUCCESS);
	}

	private TaskDefinition task() { TaskDefinition task = new TaskDefinition(); task.setId("task-1"); return task; }
	private void await(ExecutionJob job, JobStatus status) throws Exception {
		long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
		while (job.getStatus() != status && System.nanoTime() < deadline) Thread.sleep(10);
		assertEquals(status, job.getStatus());
	}
}
