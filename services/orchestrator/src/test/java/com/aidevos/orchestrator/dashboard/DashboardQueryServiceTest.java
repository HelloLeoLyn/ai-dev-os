package com.aidevos.orchestrator.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.audit.timeline.TimelineService;
import com.aidevos.orchestrator.execution.ExecutionAttempt;
import com.aidevos.orchestrator.execution.ExecutionAttemptStatus;
import com.aidevos.orchestrator.execution.InMemoryExecutionAttemptRepository;
import com.aidevos.orchestrator.execution.InMemoryExecutionRecordRepository;
import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobLease;
import com.aidevos.orchestrator.job.JobStore;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.run.InMemoryPlanRunRepository;
import com.aidevos.orchestrator.plan.run.PlanRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardQueryServiceTest {

	private JobStore jobStore;
	private InMemoryExecutionRecordRepository recordRepository;
	private InMemoryExecutionAttemptRepository attemptRepository;
	private InMemoryPlanRunRepository planRunRepository;
	private InMemoryAuditRepository auditRepository;
	private DashboardQueryService queryService;

	@BeforeEach
	void setUp() {
		jobStore = new JobStore();
		recordRepository = new InMemoryExecutionRecordRepository();
		attemptRepository = new InMemoryExecutionAttemptRepository();
		planRunRepository = new InMemoryPlanRunRepository();
		auditRepository = new InMemoryAuditRepository();
		queryService = new DashboardQueryService(jobStore, recordRepository,
			attemptRepository, planRunRepository, auditRepository,
			new TimelineService(auditRepository));
	}

	@Test
	void shouldListJobsWithLeaseAndPriority() {
		ExecutionJob job = runningJob("job-1", 5, "worker-1");

		List<JobSummaryDTO> jobs = queryService.listJobs();

		assertEquals(1, jobs.size());
		JobSummaryDTO summary = jobs.getFirst();
		assertEquals("job-1", summary.jobId());
		assertEquals("RUNNING", summary.status());
		assertEquals(5, summary.priority());
		assertEquals("worker-1", summary.leaseOwner());
		assertEquals(job.getCreatedAt(), summary.createdAt());
		assertEquals(job.getStartedAt(), summary.updatedAt());
	}

	@Test
	void shouldUseCompletedTimeAsUpdatedAtWhenPresent() {
		ExecutionJob job = runningJob("job-1", 0, null);
		job.markSucceeded(new com.aidevos.orchestrator.execution.ExecutionResult(), "record-1");

		JobSummaryDTO summary = queryService.listJobs().getFirst();

		assertEquals("SUCCESS", summary.status());
		assertEquals(job.getCompletedAt(), summary.updatedAt());
	}

	@Test
	void shouldListExecutionsWithAttemptAndFailureReason() {
		ExecutionJob job = runningJob("job-1", 0, null);
		ExecutionAttempt attempt = new ExecutionAttempt("attempt-1", "job-1", 2);
		attempt.markRunning(Instant.parse("2026-08-01T00:00:00Z"));
		attempt.markFailed("STALE_EXECUTION", Instant.parse("2026-08-01T00:01:00Z"));
		attemptRepository.save(attempt);
		recordRepository.save(record("record-1", "job-1", "exec-1", "attempt-1",
			"FAILED", "failure", Instant.parse("2026-08-01T00:00:00Z")));

		List<ExecutionSummaryDTO> executions = queryService.listExecutions();

		assertEquals(1, executions.size());
		ExecutionSummaryDTO summary = executions.getFirst();
		assertEquals("exec-1", summary.executionId());
		assertEquals("job-1", summary.jobId());
		assertEquals("FAILED", summary.status());
		assertEquals(2, summary.attempt());
		assertEquals("STALE_EXECUTION", summary.failureReason());
		assertEquals(Instant.parse("2026-08-01T00:00:00Z"), summary.createdAt());
	}

	@Test
	void shouldResolveTimelineScopeToJob() {
		jobStore.save(runningJob("job-1", 0, null));

		DashboardTimeline timeline = queryService.timeline("job-1");

		assertEquals("JOB", timeline.scopeType());
		assertEquals("job-1", timeline.scopeId());
	}

	@Test
	void shouldResolveTimelineScopeToExecutionByRecordId() {
		recordRepository.save(record("record-1", "job-1", "exec-1", null,
			"SUCCESS", null, Instant.parse("2026-08-01T00:00:00Z")));

		DashboardTimeline timeline = queryService.timeline("record-1");

		assertEquals("EXECUTION", timeline.scopeType());
		assertEquals("exec-1", timeline.scopeId());
	}

	@Test
	void shouldResolveTimelineScopeToPlanRun() {
		Plan plan = new Plan("plan-1", 1, "goal", PlanStatus.DRAFT,
			List.of(), List.of(), null, Instant.parse("2026-08-01T00:00:00Z"));
		planRunRepository.createIfAbsent("approval-1",
			new PlanRun("plan-run-1", "approval-1", plan, List.of(),
				Instant.parse("2026-08-01T00:00:00Z")));

		DashboardTimeline timeline = queryService.timeline("plan-run-1");

		assertEquals("PLAN_RUN", timeline.scopeType());
		assertEquals("plan-run-1", timeline.scopeId());
	}

	@Test
	void shouldFallBackToAuditEventsForUnknownId() {
		auditRepository.append(new EventRecord("event-1", EventType.JOB_SUBMITTED,
			Instant.parse("2026-08-01T00:00:00Z"), 0, "step-run", "step-1",
			null, null, null, null, null, null, "step-1", null, null,
			null, null, null, null, null, null, "submitted", Map.of(),
			"idem-1", 1));

		DashboardTimeline timeline = queryService.timeline("step-1");

		assertEquals("AUDIT", timeline.scopeType());
		assertEquals("step-1", timeline.scopeId());
		assertEquals(1, timeline.events().size());
		assertEquals("event-1", timeline.events().getFirst().id());
	}

	private ExecutionJob runningJob(String id, int priority, String leaseOwner) {
		TaskDefinition task = new TaskDefinition();
		task.setId("task-1");
		ExecutionJob job = new ExecutionJob(id, task);
		job.setPriority(priority);
		job.markRunning();
		if (leaseOwner != null) {
			job.applyLease(new JobLease(leaseOwner, 1L, Instant.now().plusSeconds(30)));
		}
		jobStore.save(job);
		return job;
	}

	private ExecutionRecord record(String id, String jobId, String executionId,
			String attemptId, String status, String message, Instant startedAt) {
		ExecutionRecord record = new ExecutionRecord();
		record.setId(id);
		record.setJobId(jobId);
		record.setExecutionId(executionId);
		record.setAttemptId(attemptId);
		record.setStatus(status);
		record.setMessage(message);
		record.setStartedAt(startedAt);
		return record;
	}
}
