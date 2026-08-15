package com.aidevos.orchestrator.job;

import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobServiceTest {

	@Test
	void shouldSubmitQueuedJob() {
		JobStore store = new JobStore();
		JobWorker worker = new JobWorker(mock(ExecutionEngine.class), 2);
		JobService service = new JobService(store, worker);

		JobSubmissionResponse response = service.submit(task("original"));

		assertEquals(JobStatus.QUEUED, response.status());
		assertEquals(JobStatus.QUEUED, store.get(response.jobId()).getStatus());
	}

	@Test
	void shouldRejectSubmissionWhenQueueIsFull() {
		JobStore store = new JobStore();
		JobWorker worker = new JobWorker(mock(ExecutionEngine.class), 1);
		JobService service = new JobService(store, worker);
		service.submit(task("first"));

		assertThrows(JobQueueFullException.class, () -> service.submit(task("second")));
		assertEquals(1, store.getAll().size());
	}

	@Test
	void shouldSaveTaskSnapshot() {
		JobStore store = new JobStore();
		JobWorker worker = new JobWorker(mock(ExecutionEngine.class), 1);
		JobService service = new JobService(store, worker);
		TaskDefinition original = task("original");
		original.setRequiredCapabilities(new ArrayList<>(List.of("coding")));
		Map<String, Object> browser = new LinkedHashMap<>();
		browser.put("action", "navigate");
		original.setParameters(new LinkedHashMap<>(Map.of("browser", browser)));

		JobSubmissionResponse response = service.submit(original);
		original.setDescription("changed");
		original.getRequiredCapabilities().add("git");
		browser.put("url", "https://changed.example");

		TaskDefinition snapshot = store.get(response.jobId()).getTaskSnapshot();
		assertEquals("original", snapshot.getDescription());
		assertEquals(List.of("coding"), snapshot.getRequiredCapabilities());
		assertEquals(Map.of("action", "navigate"), snapshot.getParameters().get("browser"));
	}

	@Test
	void shouldRestoreWaitingStatusWhenApprovalRequeueFails() {
		JobStore store = new JobStore();
		JobWorker worker = mock(JobWorker.class);
		JobService service = new JobService(store, worker);
		ExecutionJob job = new ExecutionJob("job-1", task("coding"));
		job.markRunning();
		com.aidevos.orchestrator.execution.ExecutionResult result =
			new com.aidevos.orchestrator.execution.ExecutionResult();
		result.setApprovalRequired(true);
		result.setApprovalId("approval-1");
		job.markWaitingApproval(result, "record-1");
		store.save(job);
		when(worker.submit(job)).thenReturn(false);

		assertEquals(false, service.resumeAfterApproval("job-1"));
		assertEquals(JobStatus.WAITING_APPROVAL, job.getStatus());
	}

	@Test
	void shouldResumeTheSameJobMoreThanOnce() {
		JobStore store = new JobStore();
		JobWorker worker = mock(JobWorker.class);
		when(worker.submit(any())).thenReturn(true);
		JobService service = new JobService(store, worker);
		ExecutionJob job = new ExecutionJob("job-1", task("coding"));
		job.markRunning();
		com.aidevos.orchestrator.execution.ExecutionResult first = new com.aidevos.orchestrator.execution.ExecutionResult();
		first.setApprovalRequired(true); first.setApprovalId("approval-a");
		job.markWaitingApproval(first, "record-1"); store.save(job);

		assertEquals(true, service.resumeAfterApproval("job-1"));
		assertEquals("job-1", store.get("job-1").getId());
		job.markRunning();
		com.aidevos.orchestrator.execution.ExecutionResult second = new com.aidevos.orchestrator.execution.ExecutionResult();
		second.setApprovalRequired(true); second.setApprovalId("approval-b");
		job.markWaitingApproval(second, "record-2"); store.save(job);

		assertEquals(true, service.resumeAfterApproval("job-1"));
		assertEquals("job-1", store.get("job-1").getId());
		verify(worker, times(2)).submit(any());
	}

	@Test
	void shouldSubmitIdempotentlyWithDeterministicJobId() {
		JobStore store = new JobStore();
		JobWorker worker = mock(JobWorker.class);
		when(worker.submit(any())).thenReturn(true);
		JobService service = new JobService(store, worker);

		JobSubmissionResponse first = service.submit(task("original"), "job-run-approval-1");
		JobSubmissionResponse second = service.submit(task("original"), "job-run-approval-1");

		assertEquals(first.jobId(), second.jobId());
		assertEquals("job-run-approval-1", first.jobId());
		assertEquals(1, store.getAll().size());
		verify(worker, times(1)).submit(any());
	}

	@Test
	void shouldRejectBlankIdempotencyKey() {
		JobStore store = new JobStore();
		JobWorker worker = mock(JobWorker.class);
		JobService service = new JobService(store, worker);

		assertThrows(IllegalArgumentException.class, () -> service.submit(task("original"), " "));
	}

	private TaskDefinition task(String description) {
		TaskDefinition task = new TaskDefinition();
		task.setId("task-1");
		task.setDescription(description);
		return task;
	}
}
