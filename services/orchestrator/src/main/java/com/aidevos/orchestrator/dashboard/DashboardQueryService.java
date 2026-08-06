package com.aidevos.orchestrator.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.aidevos.orchestrator.audit.AuditRepository;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.query.AuditEventView;
import com.aidevos.orchestrator.audit.timeline.TimelineService;
import com.aidevos.orchestrator.execution.ExecutionAttempt;
import com.aidevos.orchestrator.execution.ExecutionAttemptRepository;
import com.aidevos.orchestrator.execution.ExecutionRecordRepository;
import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobRepository;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.plan.run.PlanRunRepository;
import org.springframework.stereotype.Service;

/**
 * Read-only queries backing the Phase 9-A-2 dashboard monitoring pages. All
 * data is derived from existing repositories; no execution flow is changed.
 */
@Service
public class DashboardQueryService {

	private final JobRepository jobRepository;
	private final ExecutionRecordRepository executionRecordRepository;
	private final ExecutionAttemptRepository attemptRepository;
	private final PlanRunRepository planRunRepository;
	private final AuditRepository auditRepository;
	private final TimelineService timelineService;

	public DashboardQueryService(JobRepository jobRepository,
			ExecutionRecordRepository executionRecordRepository,
			ExecutionAttemptRepository attemptRepository,
			PlanRunRepository planRunRepository,
			AuditRepository auditRepository,
			TimelineService timelineService) {
		this.jobRepository = jobRepository;
		this.executionRecordRepository = executionRecordRepository;
		this.attemptRepository = attemptRepository;
		this.planRunRepository = planRunRepository;
		this.auditRepository = auditRepository;
		this.timelineService = timelineService;
	}

	public List<JobSummaryDTO> listJobs() {
		return jobRepository.getAll().stream()
			.map(this::jobSummary)
			.toList();
	}

	public List<ExecutionSummaryDTO> listExecutions() {
		return executionRecordRepository.getAll().stream()
			.map(this::executionSummary)
			.toList();
	}

	public DashboardTimeline timeline(String id) {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("Timeline id is required");
		}
		if (jobRepository.get(id) != null) {
			return from("JOB", id,
				timelineService.job(id, Set.<EventType>of(), 0, 100).events());
		}
		ExecutionRecord record = findExecutionRecord(id);
		if (record != null) {
			String executionId = record.getExecutionId() != null
				? record.getExecutionId() : record.getId();
			return from("EXECUTION", executionId,
				timelineService.execution(executionId, Set.<EventType>of(), 0, 100).events());
		}
		if (planRunRepository.get(id) != null) {
			return from("PLAN_RUN", id,
				timelineService.planRun(id, Set.<EventType>of(), 0, 100).events());
		}
		return new DashboardTimeline("AUDIT", id, auditEventsByAggregate(id));
	}

	private JobSummaryDTO jobSummary(ExecutionJob job) {
		return new JobSummaryDTO(job.getId(), job.getStatus().name(), job.getPriority(),
			job.getLeaseOwner(), job.getCreatedAt(), updatedAt(job));
	}

	private Instant updatedAt(ExecutionJob job) {
		if (job.getCompletedAt() != null) {
			return job.getCompletedAt();
		}
		if (job.getHeartbeatAt() != null) {
			return job.getHeartbeatAt();
		}
		if (job.getStartedAt() != null) {
			return job.getStartedAt();
		}
		return job.getCreatedAt();
	}

	private ExecutionSummaryDTO executionSummary(ExecutionRecord record) {
		ExecutionAttempt attempt = record.getAttemptId() == null ? null
			: attemptRepository.get(record.getAttemptId());
		String executionId = record.getExecutionId() != null
			? record.getExecutionId() : record.getId();
		String failureReason = attempt != null && attempt.getFailureCode() != null
			? attempt.getFailureCode() : record.getMessage();
		Instant createdAt = record.getStartedAt() != null
			? record.getStartedAt() : record.getCompletedAt();
		return new ExecutionSummaryDTO(executionId, record.getJobId(), record.getStatus(),
			attempt == null ? 0 : attempt.getAttemptNo(), failureReason, createdAt);
	}

	private ExecutionRecord findExecutionRecord(String id) {
		ExecutionRecord record = executionRecordRepository.get(id);
		if (record != null) {
			return record;
		}
		return executionRecordRepository.getAll().stream()
			.filter(candidate -> id.equals(candidate.getExecutionId()))
			.findFirst()
			.orElse(null);
	}

	private List<AuditEventView> auditEventsByAggregate(String id) {
		EventQuery aggregateQuery = new EventQuery(null, id, null, null, null, null, null,
			null, null, null, Set.of(), null, null, 0, 100);
		List<AuditEventView> events = auditRepository.query(aggregateQuery).stream()
			.map(AuditEventView::from)
			.toList();
		if (!events.isEmpty()) {
			return events;
		}
		EventQuery stepQuery = new EventQuery(null, null, null, id, null, null, null,
			null, null, null, Set.of(), null, null, 0, 100);
		return auditRepository.query(stepQuery).stream()
			.map(AuditEventView::from)
			.toList();
	}

	private DashboardTimeline from(String scopeType, String scopeId,
			List<AuditEventView> events) {
		return new DashboardTimeline(scopeType, scopeId, events);
	}
}
