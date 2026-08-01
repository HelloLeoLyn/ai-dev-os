package com.aidevos.orchestrator.dashboard;

import java.util.List;

import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobStatus;
import com.aidevos.orchestrator.job.JobStore;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.task.TaskManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardServiceTest {

	private TaskManager taskManager;
	private JobStore jobStore;
	private ExecutionRecordManager executionRecordManager;
	private DashboardService dashboardService;

	@BeforeEach
	void setUp() {
		taskManager = new TaskManager();
		jobStore = new JobStore();
		executionRecordManager = new ExecutionRecordManager();
		dashboardService = new DashboardService(taskManager, jobStore, executionRecordManager);
	}

	@Test
	void shouldReturnEmptyDashboard() {
		DashboardSummary summary = dashboardService.getSummary();

		assertNotNull(summary.generatedAt());
		assertEquals(0, summary.tasks().total());
		assertTrue(summary.tasks().byStatus().isEmpty());
		assertEquals(0, summary.jobs().total());
		assertEquals(0.0, summary.jobs().successRate());
		assertEquals(0, summary.executions().total());
		assertEquals(0.0, summary.executions().successRate());
		assertTrue(summary.recentJobs().isEmpty());
	}

	@Test
	void shouldAggregateTaskStatuses() {
		registerTask("task-1", "pending");
		registerTask("task-2", "pending");
		registerTask("task-3", "completed");
		registerTask("task-4", null);

		TaskStatistics statistics = dashboardService.getSummary().tasks();

		assertEquals(4, statistics.total());
		assertEquals(2, statistics.byStatus().get("pending"));
		assertEquals(1, statistics.byStatus().get("completed"));
		assertEquals(1, statistics.byStatus().get("UNKNOWN"));
	}

	@Test
	void shouldAggregateJobStatusesUsingTerminalSuccessRate() {
		jobStore.save(job("queued", JobStatus.QUEUED));
		jobStore.save(job("running", JobStatus.RUNNING));
		jobStore.save(job("success-1", JobStatus.SUCCESS));
		jobStore.save(job("success-2", JobStatus.SUCCESS));
		jobStore.save(job("failed", JobStatus.FAILED));

		JobStatistics statistics = dashboardService.getSummary().jobs();

		assertEquals(5, statistics.total());
		assertEquals(1, statistics.queued());
		assertEquals(1, statistics.running());
		assertEquals(2, statistics.succeeded());
		assertEquals(1, statistics.failed());
		assertEquals(66.67, statistics.successRate());
	}

	@Test
	void shouldAggregateExecutionRecordsSeparately() {
		executionRecordManager.save(record("record-1", "SUCCESS"));
		executionRecordManager.save(record("record-2", "FAILED"));
		executionRecordManager.save(record("record-3", "other"));

		ExecutionStatistics statistics = dashboardService.getSummary().executions();

		assertEquals(3, statistics.total());
		assertEquals(1, statistics.successful());
		assertEquals(1, statistics.failed());
		assertEquals(1, statistics.unknown());
		assertEquals(50.0, statistics.successRate());
	}

	@Test
	void shouldReturnTenMostRecentJobsWithoutFullOutput() {
		for (int index = 0; index < 12; index++) {
			jobStore.save(job("job-%02d".formatted(index), JobStatus.SUCCESS));
		}

		List<RecentJobSummary> recentJobs = dashboardService.getSummary().recentJobs();

		assertEquals(10, recentJobs.size());
		assertEquals("job-11", recentJobs.getFirst().id());
		assertEquals("job-02", recentJobs.getLast().id());
		assertEquals("completed", recentJobs.getFirst().resultSummary());
	}

	private void registerTask(String id, String status) {
		TaskDefinition task = new TaskDefinition();
		task.setId(id);
		task.setStatus(status);
		taskManager.register(task);
	}

	private ExecutionJob job(String id, JobStatus status) {
		TaskDefinition task = new TaskDefinition();
		task.setId("task-1");
		ExecutionJob job = new ExecutionJob(id, task);
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(status == JobStatus.SUCCESS);
		result.setMessage("completed");
		result.setOutput("full output must not be copied");
		if (status == JobStatus.RUNNING) {
			job.markRunning();
		}
		else if (status == JobStatus.SUCCESS) {
			job.markSucceeded(result, "record-1");
		}
		else if (status == JobStatus.FAILED) {
			job.markFailed(result, "failed", "record-1");
		}
		return job;
	}

	private ExecutionRecord record(String id, String status) {
		ExecutionRecord record = new ExecutionRecord();
		record.setId(id);
		record.setStatus(status);
		return record;
	}
}
