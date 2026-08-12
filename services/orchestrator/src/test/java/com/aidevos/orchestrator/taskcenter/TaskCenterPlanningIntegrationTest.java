package com.aidevos.orchestrator.taskcenter;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.executor.ExecutorRegistry;
import com.aidevos.orchestrator.execution.InMemoryExecutionRecordRepository;
import com.aidevos.orchestrator.job.JobStore;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.plan.PlanSnapshot;
import com.aidevos.orchestrator.plan.PlanSnapshotFactory;
import com.aidevos.orchestrator.plan.PlanValidator;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import com.aidevos.orchestrator.plan.approval.PlanApprovalStore;
import com.aidevos.orchestrator.plan.run.InMemoryPlanRunRepository;
import com.aidevos.orchestrator.planner.HermesPlanner;
import com.aidevos.orchestrator.planner.PlannerService;
import com.aidevos.orchestrator.planner.replan.ReplanValidator;
import com.aidevos.orchestrator.tool.ToolAccess;
import com.aidevos.orchestrator.tool.ToolDefinition;
import com.aidevos.orchestrator.tool.ToolRegistry;
import com.aidevos.orchestrator.task.TaskManager;
import com.aidevos.orchestrator.timeline.TimelineService;
import com.aidevos.orchestrator.timeline.UnifiedTimeline;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskCenterPlanningIntegrationTest {

	@Test
	void firstReadOnlyTaskCapturesSnapshotBeforeHermesPlanningAndRequestsApproval() {
		InMemoryAuditRepository events = new InMemoryAuditRepository();
		AuditService audit = new AuditService(events);
		PlanValidator validator = new PlanValidator();
		PlanSnapshotFactory snapshots = snapshotFactory();
		PlannerService planners = new PlannerService(List.of(new HermesPlanner()), validator,
			new ReplanValidator(validator), audit, snapshots, "v1");
		PlanApprovalService approvals = new PlanApprovalService(new PlanApprovalStore(), validator,
			new ObjectMapper(), audit);
		TaskCenterService tasks = new TaskCenterService(planners, approvals,
			new InMemoryPlanRunRepository(), null, audit, new InMemoryTaskRepository());

		TaskRecord task = tasks.createTask(new CreateTaskRequest("Analyze JJX", "Read only analysis",
			"Analyze the current project", "hermes", "project-1", "workspace-1",
			ExecutionMode.READ_ONLY), "/workspace/jjx");

		assertEquals(TaskStatus.PLANNING, task.getStatus());
		assertNotNull(task.getApprovalId());
		assertNull(task.getPlanRunId());
		assertFalse(events.query(EventQuery.all()).stream()
			.anyMatch(event -> event.type() == EventType.PLAN_VALIDATION_FAILED));
		assertTrue(events.query(EventQuery.all()).stream()
			.anyMatch(event -> event.type() == EventType.PLAN_CREATED));
		assertTrue(events.query(EventQuery.all()).stream()
			.anyMatch(event -> event.type() == EventType.PLAN_APPROVAL_REQUESTED));
		List<EventType> expectedEvents = List.of(EventType.USER_OPERATION, EventType.PLAN_CREATED,
			EventType.PLAN_APPROVAL_REQUESTED);
		assertTrue(events.query(EventQuery.all()).stream()
			.filter(event -> expectedEvents.contains(event.type()))
			.allMatch(event -> task.getTaskId().equals(event.taskId())));
		TimelineService timelineService = new TimelineService(events,
			new InMemoryPlanRunRepository(), new JobStore(),
			new InMemoryExecutionRecordRepository(), new TaskManager(), tasks);
		UnifiedTimeline timeline = timelineService.timeline(task.getTaskId());
		assertEquals("TASK", timeline.scopeType());
		assertEquals(task.getTaskId(), timeline.scopeId());
		assertEquals(expectedEvents, timeline.events().stream()
			.map(event -> EventType.valueOf(event.eventType())).toList());

		PlanApprovalRequest approval = approvals.get(task.getApprovalId());
		assertNotNull(approval);
		PlanSnapshot snapshot = approval.getPlan().snapshot();
		assertEquals("v1", snapshot.policyVersion());
		assertEquals(List.of("hermes-agent", "analyst"), snapshot.agents().stream()
			.map(PlanSnapshot.AgentSnapshot::name).toList());
		assertEquals(Set.of("planning", "analysis", "read-only"), snapshot.capabilities());
		assertEquals(List.of("read_text_file"), snapshot.tools().stream()
			.map(PlanSnapshot.ToolSnapshot::name).toList());
		assertEquals(Set.of("hermes-executor", "codex"), snapshot.executors());
		assertEquals("project-1", snapshot.plannerMetadata().get("projectId"));
		assertEquals("workspace-1", snapshot.plannerMetadata().get("workspaceId"));
		assertEquals("/workspace/jjx", snapshot.plannerMetadata().get("workspacePath"));
		assertEquals("READ_ONLY", snapshot.plannerMetadata().get("executionMode"));
	}

	private PlanSnapshotFactory snapshotFactory() {
		AgentDefinition agent = new AgentDefinition();
		agent.setName("hermes-agent");
		agent.setExecutor("hermes-executor");
		agent.setCapabilities(List.of("planning"));
		agent.setPermissionLevel("read-only");
		agent.setEnabled(true);
		AgentDefinition analyst = new AgentDefinition();
		analyst.setName("analyst");
		analyst.setExecutor("codex");
		analyst.setCapabilities(List.of("analysis", "read-only"));
		analyst.setPermissionLevel("read-only");
		analyst.setEnabled(true);
		AgentManager agents = mock(AgentManager.class);
		when(agents.getAllAgents()).thenReturn(List.of(agent, analyst));
		ToolRegistry tools = mock(ToolRegistry.class);
		when(tools.getTools()).thenReturn(List.of(new ToolDefinition("filesystem",
			"read_text_file", "Read a file", Map.of(), ToolAccess.READ_ONLY)));
		ExecutorRegistry executors = mock(ExecutorRegistry.class);
		when(executors.getTypes()).thenReturn(Set.of("hermes-executor", "codex"));
		return new PlanSnapshotFactory(agents, tools, executors);
	}
}
