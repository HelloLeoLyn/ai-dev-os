package com.aidevos.orchestrator.timeline;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.execution.InMemoryExecutionRecordRepository;
import com.aidevos.orchestrator.job.JobStore;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.run.InMemoryPlanRunRepository;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.task.TaskManager;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TimelineServiceTest {

	private InMemoryAuditRepository auditRepository;
	private InMemoryPlanRunRepository planRunRepository;
	private JobStore jobStore;
	private InMemoryExecutionRecordRepository recordRepository;
	private TaskManager taskManager;
	private TaskCenterService taskCenterService;
	private TimelineService service;

	@BeforeEach
	void setUp() {
		auditRepository = new InMemoryAuditRepository();
		planRunRepository = new InMemoryPlanRunRepository();
		jobStore = new JobStore();
		recordRepository = new InMemoryExecutionRecordRepository();
		taskManager = new TaskManager();
		taskCenterService = mock(TaskCenterService.class);
		when(taskCenterService.getTask(any())).thenReturn(Optional.empty());
		service = new TimelineService(auditRepository, planRunRepository, jobStore,
			recordRepository, taskManager, taskCenterService);
	}

	@Test
	void shouldResolveExecutionScopeByRecordId() {
		recordRepository.save(record("record-1", "exec-1"));
		append(new EventRecord("event-1", EventType.EXECUTION_STARTED,
			Instant.parse("2026-08-01T00:00:00Z"), 0, "execution", "exec-1",
			null, "RUNNING", null, null, null, null, null, null, null, "exec-1",
			null, null, null, null, null, "started", Map.of(), "idem-1", 1));

		UnifiedTimeline timeline = service.timeline("record-1");

		assertEquals("EXECUTION", timeline.scopeType());
		assertEquals("exec-1", timeline.scopeId());
		assertEquals(1, timeline.events().size());
		TimelineEventDTO event = timeline.events().getFirst();
		assertEquals("EXECUTION_STARTED", event.eventType());
		assertEquals("EXECUTION", event.sourceType());
		assertEquals("exec-1", event.sourceId());
		assertEquals("RUNNING", event.status());
		assertEquals("started", event.message());
	}

	@Test
	void shouldResolveJobScope() {
		com.aidevos.orchestrator.job.ExecutionJob job = new com.aidevos.orchestrator.job.ExecutionJob(
			"job-1", task("task-1"));
		jobStore.save(job);
		append(new EventRecord("event-1", EventType.JOB_STARTED,
			Instant.parse("2026-08-01T00:00:00Z"), 0, "job", "job-1",
			"QUEUED", "RUNNING", null, null, null, null, null, null, "job-1", null,
			null, null, null, null, null, "started", Map.of(), "idem-1", 1));

		UnifiedTimeline timeline = service.timeline("job-1");

		assertEquals("JOB", timeline.scopeType());
		assertEquals("job-1", timeline.scopeId());
		assertEquals("JOB", timeline.events().getFirst().sourceType());
		assertEquals("job-1", timeline.events().getFirst().sourceId());
	}

	@Test
	void shouldResolvePlanRunScope() {
		Plan plan = new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(), List.of(),
			null, Instant.parse("2026-08-01T00:00:00Z"));
		planRunRepository.createIfAbsent("approval-1",
			new PlanRun("plan-run-1", "approval-1", plan, List.of(),
				Instant.parse("2026-08-01T00:00:00Z")));

		UnifiedTimeline timeline = service.timeline("plan-run-1");

		assertEquals("PLAN_RUN", timeline.scopeType());
		assertEquals("plan-run-1", timeline.scopeId());
	}

	@Test
	void shouldResolveTaskScope() {
		taskManager.register(task("task-1"));
		append(new EventRecord("event-1", EventType.PLAN_RUN_CREATED,
			Instant.parse("2026-08-01T00:00:00Z"), 0, "plan-run", "plan-run-1",
			null, "DRAFT", "task-1", null, null, "plan-run-1", null, null, null, null,
			null, null, null, null, null, "created", Map.of(), "idem-1", 1));

		UnifiedTimeline timeline = service.timeline("task-1");

		assertEquals("TASK", timeline.scopeType());
		assertEquals("task-1", timeline.scopeId());
		assertEquals(1, timeline.events().size());
		assertEquals("PLAN_RUN", timeline.events().getFirst().sourceType());
	}

	@Test
	void shouldResolveTaskCenterTaskScope() {
		when(taskCenterService.getTask("task-center-1")).thenReturn(Optional.of(
			new TaskRecord("task-center-1", "Login flow", "Implement login flow")));
		append(new EventRecord("event-1", EventType.AGENT_PLAN_CREATED,
			Instant.parse("2026-08-01T00:00:00Z"), 0, "agent-plan", "plan-1",
			null, "PENDING", "task-center-1", null, null, null, null, null, null,
			null, null, null, null, "AGENT", "coder", "Agent plan created",
			Map.of(), "idem-1", 1));

		UnifiedTimeline timeline = service.timeline("task-center-1");

		assertEquals("TASK", timeline.scopeType());
		assertEquals("task-center-1", timeline.scopeId());
		assertEquals(1, timeline.events().size());
		assertEquals("AGENT_PLAN_CREATED", timeline.events().getFirst().eventType());
		assertEquals("TASK", timeline.events().getFirst().sourceType());
	}

	@Test
	void shouldResolveHistoricalTaskEventsWithoutFormalTaskId() {
		String taskId = "task-center-legacy";
		when(taskCenterService.getTask(taskId)).thenReturn(Optional.of(
			new TaskRecord(taskId, "Legacy task", "Historical audit events")));
		append(new EventRecord("event-task", EventType.USER_OPERATION,
			Instant.parse("2026-08-01T00:00:00Z"), 0, "task", taskId,
			null, null, null, null, null, null, null, null, null, null,
			null, null, null, "USER", "USER", "submitted", Map.of(), "idem-task", 1));
		append(new EventRecord("event-plan", EventType.PLAN_CREATED,
			Instant.parse("2026-08-01T00:00:01Z"), 0, "planning-request", taskId,
			null, "CREATED", null, null, null, null, null, null, null, null,
			null, null, null, "USER", taskId, "created", Map.of(), "idem-plan", 1));
		append(new EventRecord("event-approval", EventType.PLAN_APPROVAL_REQUESTED,
			Instant.parse("2026-08-01T00:00:02Z"), 0, "plan-approval", "approval-1",
			null, "PENDING", null, "plan-1", 1, null, null, null, null, null,
			null, null, "approval-1", "SYSTEM", null, "approval requested",
			Map.of("requestId", taskId), "idem-approval", 1));

		UnifiedTimeline timeline = service.timeline(taskId);

		assertEquals(List.of("USER_OPERATION", "PLAN_CREATED", "PLAN_APPROVAL_REQUESTED"),
			timeline.events().stream().map(TimelineEventDTO::eventType).toList());
	}

	@Test
	void shouldFallBackToAuditEvents() {
		append(new EventRecord("event-1", EventType.STEP_SUCCEEDED,
			Instant.parse("2026-08-01T00:00:00Z"), 0, "step-run", "step-1",
			"RUNNING", "SUCCEEDED", null, null, null, null, "step-1", null, null, null,
			null, null, null, null, null, "done", Map.of(), "idem-1", 1));

		UnifiedTimeline timeline = service.timeline("step-1");

		assertEquals("AUDIT", timeline.scopeType());
		assertEquals("step-1", timeline.scopeId());
		assertEquals("STEP_RUN", timeline.events().getFirst().sourceType());
	}

	@Test
	void shouldRejectBlankId() {
		assertThrows(IllegalArgumentException.class, () -> service.timeline(" "));
	}

	private void append(EventRecord event) {
		auditRepository.append(event);
	}

	private ExecutionRecord record(String id, String executionId) {
		ExecutionRecord record = new ExecutionRecord();
		record.setId(id);
		record.setExecutionId(executionId);
		return record;
	}

	private TaskDefinition task(String id) {
		TaskDefinition task = new TaskDefinition();
		task.setId(id);
		return task;
	}
}
