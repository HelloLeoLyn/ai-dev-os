package com.aidevos.orchestrator.memory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.execution.InMemoryExecutionRecordRepository;
import com.aidevos.orchestrator.job.JobStore;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import com.aidevos.orchestrator.plan.run.PlanRunRepository;
import com.aidevos.orchestrator.planner.PlannerService;
import com.aidevos.orchestrator.planner.PlanningRequest;
import com.aidevos.orchestrator.planner.PlanningResult;
import com.aidevos.orchestrator.task.TaskManager;
import com.aidevos.orchestrator.taskcenter.CreateTaskRequest;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.timeline.TimelineEventDTO;
import com.aidevos.orchestrator.timeline.TimelineService;
import com.aidevos.orchestrator.timeline.UnifiedTimeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 16-B timeline verification: MEMORY_SEARCHED / MEMORY_MATCHED /
 * MEMORY_APPLIED events carry the taskId and appear on the task timeline
 * before agent execution.
 */
class MemoryTimelineTest {

	@Test
	void shouldShowMemoryEventsOnTaskTimeline() {
		InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
		AuditService auditService = new AuditService(auditRepository);
		PlannerService plannerService = mock(PlannerService.class);
		PlanApprovalService approvalService = mock(PlanApprovalService.class);
		PlanRunRepository planRunRepository = mock(PlanRunRepository.class);
		when(plannerService.createPlan(any(PlanningRequest.class))).thenReturn(
			PlanningResult.success("hermes", null,
				new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(), List.of(),
					null, Instant.parse("2026-08-01T00:00:00Z"))));
		PlanApprovalRequest approval = new PlanApprovalRequest("approval-1", "task-1",
			new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(), List.of(), null,
				Instant.parse("2026-08-01T00:00:00Z")), "hash",
			Instant.parse("2026-08-01T00:00:00Z"));
		when(approvalService.create(any(), any())).thenReturn(approval);
		TaskCenterService taskCenterService = new TaskCenterService(plannerService,
			approvalService, planRunRepository);
		TaskRecord task = taskCenterService.createTask(new CreateTaskRequest(
			"修复事务失效", "修复 Spring Boot 事务失效", "修复 Spring Boot 事务失效", "hermes",
			"project-x", null));

		auditService.memoryEvent(EventType.MEMORY_SEARCHED, task.getTaskId(), "事务失效",
			2, List.of(), "Memory search executed", Map.of());
		auditService.memoryEvent(EventType.MEMORY_MATCHED, task.getTaskId(), "事务失效",
			2, List.of("mem-1", "mem-2"), "Memory records matched", Map.of());
		auditService.memoryEvent(EventType.MEMORY_APPLIED, task.getTaskId(), "事务失效",
			2, List.of(), "Memory hints applied", Map.of("graphId", "graph-1"));

		TimelineService timelineService = new TimelineService(auditRepository,
			planRunRepository, new JobStore(), new InMemoryExecutionRecordRepository(),
			new TaskManager(), taskCenterService);
		UnifiedTimeline timeline = timelineService.timeline(task.getTaskId());

		assertEquals("TASK", timeline.scopeType());
		List<String> eventTypes = timeline.events().stream()
			.map(TimelineEventDTO::eventType).toList();
		for (String expected : List.of("MEMORY_SEARCHED", "MEMORY_MATCHED",
			"MEMORY_APPLIED")) {
			assertTrue(eventTypes.contains(expected), "missing " + expected + ": " + eventTypes);
		}

		TimelineEventDTO matched = timeline.events().stream()
			.filter(event -> "MEMORY_MATCHED".equals(event.eventType())).findFirst()
			.orElseThrow();
		assertEquals(task.getTaskId(), matched.sourceId());
	}
}
