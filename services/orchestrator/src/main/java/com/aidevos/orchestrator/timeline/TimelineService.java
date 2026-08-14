package com.aidevos.orchestrator.timeline;

import java.util.List;

import com.aidevos.orchestrator.audit.AuditRepository;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.execution.ExecutionRecordRepository;
import com.aidevos.orchestrator.job.JobRepository;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.plan.run.PlanRunRepository;
import com.aidevos.orchestrator.task.TaskManager;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Unified task-execution timeline: Task -> PlanRun -> StepRun -> Job ->
 * Execution -> Audit. The scope is resolved automatically from the id by
 * checking execution records, jobs, plan runs and tasks in order, falling back
 * to audit events keyed by aggregate or step id. Read-only; no execution flow
 * is changed.
 */
@Service("unifiedTimelineService")
public class TimelineService {

	private final AuditRepository auditRepository;
	private final PlanRunRepository planRunRepository;
	private final JobRepository jobRepository;
	private final ExecutionRecordRepository executionRecordRepository;
	private final TaskManager taskManager;
	private final TaskCenterService taskCenterService;

	@Autowired
	public TimelineService(AuditRepository auditRepository,
			PlanRunRepository planRunRepository, JobRepository jobRepository,
			ExecutionRecordRepository executionRecordRepository, TaskManager taskManager,
			TaskCenterService taskCenterService) {
		this.auditRepository = auditRepository;
		this.planRunRepository = planRunRepository;
		this.jobRepository = jobRepository;
		this.executionRecordRepository = executionRecordRepository;
		this.taskManager = taskManager;
		this.taskCenterService = taskCenterService;
	}

	public UnifiedTimeline timeline(String id) {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("Timeline id is required");
		}
		ExecutionRecord record = findExecutionRecord(id);
		if (record != null) {
			String executionId = record.getExecutionId() != null
				? record.getExecutionId() : record.getId();
			return build("EXECUTION", executionId, eventsByExecution(executionId));
		}
		if (jobRepository.get(id) != null) {
			return build("JOB", id, eventsByJob(id));
		}
		if (planRunRepository.get(id) != null) {
			return build("PLAN_RUN", id, eventsByPlanRun(id));
		}
		if (taskManager.getTask(id) != null) {
			return build("TASK", id, eventsByTask(id));
		}
		if (taskCenterService.getTask(id).isPresent()) {
			return build("TASK", id, eventsByTask(id));
		}
		return new UnifiedTimeline("AUDIT", id, eventsByAggregateOrStep(id));
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

	private List<TimelineEventDTO> eventsByExecution(String executionId) {
		return events(query(null, executionId, null, null));
	}

	private List<TimelineEventDTO> eventsByJob(String jobId) {
		return events(query(null, null, jobId, null));
	}

	private List<TimelineEventDTO> eventsByPlanRun(String planRunId) {
		return events(query(planRunId, null, null, null));
	}

	private List<TimelineEventDTO> eventsByTask(String taskId) {
		EventQuery all = new EventQuery(null, null, null, null, null, null, null,
			null, null, null, java.util.Set.of(), null, null, 0, EventQuery.MAX_LIMIT);
		return events(all, event -> belongsToTask(event, taskId));
	}

	private boolean belongsToTask(EventRecord event, String taskId) {
		if (taskId.equals(event.taskId())) {
			return true;
		}
		if (taskId.equals(event.aggregateId())
				&& ("task".equals(event.aggregateType())
					|| "planning-request".equals(event.aggregateType()))) {
			return true;
		}
		return "plan-approval".equals(event.aggregateType())
			&& taskId.equals(event.metadata().get("requestId"));
	}

	private List<TimelineEventDTO> eventsByAggregateOrStep(String id) {
		EventQuery aggregate = new EventQuery(null, id, null, null, null, null, null,
			null, null, null, java.util.Set.of(), null, null, 0, EventQuery.MAX_LIMIT);
		List<TimelineEventDTO> result = events(aggregate, event -> true);
		if (!result.isEmpty()) {
			return result;
		}
		EventQuery step = new EventQuery(null, null, null, id, null, null, null,
			null, null, null, java.util.Set.of(), null, null, 0, EventQuery.MAX_LIMIT);
		return events(step, event -> true);
	}

	private EventQuery query(String planRunId, String executionId, String jobId, String stepRunId) {
		return new EventQuery(null, null, planRunId, stepRunId, null, jobId, executionId,
			null, null, null, java.util.Set.of(), null, null, 0, EventQuery.MAX_LIMIT);
	}

	private List<TimelineEventDTO> events(EventQuery query) {
		return events(query, event -> true);
	}

	private List<TimelineEventDTO> events(EventQuery query,
			java.util.function.Predicate<EventRecord> filter) {
		return auditRepository.query(query).stream()
			.filter(filter)
			.map(this::toEvent)
			.toList();
	}

	private TimelineEventDTO toEvent(EventRecord event) {
		String status = event.toStatus() != null ? event.toStatus() : event.fromStatus();
		return new TimelineEventDTO(event.id(), event.type().name(), sourceType(event),
			sourceId(event), status, event.summary(), event.occurredAt());
	}

	private String sourceType(EventRecord event) {
		if ("backlog-item".equals(event.aggregateType())) {
			return "BACKLOG";
		}
		if ("validation-run".equals(event.aggregateType())) {
			return "VALIDATION";
		}
		if (event.planRunId() != null) {
			return "PLAN_RUN";
		}
		if (event.stepRunId() != null) {
			return "STEP_RUN";
		}
		if (event.jobId() != null) {
			return "JOB";
		}
		if (event.executionId() != null) {
			return "EXECUTION";
		}
		if (event.taskId() != null) {
			return "TASK";
		}
		return "AUDIT";
	}

	private String sourceId(EventRecord event) {
		if ("backlog-item".equals(event.aggregateType())) {
			return event.aggregateId();
		}
		if ("validation-run".equals(event.aggregateType())) {
			return event.aggregateId();
		}
		if (event.planRunId() != null) {
			return event.planRunId();
		}
		if (event.stepRunId() != null) {
			return event.stepRunId();
		}
		if (event.jobId() != null) {
			return event.jobId();
		}
		if (event.executionId() != null) {
			return event.executionId();
		}
		if (event.taskId() != null) {
			return event.taskId();
		}
		return event.aggregateId();
	}

	private UnifiedTimeline build(String scopeType, String scopeId,
			List<TimelineEventDTO> events) {
		return new UnifiedTimeline(scopeType, scopeId, events);
	}
}
