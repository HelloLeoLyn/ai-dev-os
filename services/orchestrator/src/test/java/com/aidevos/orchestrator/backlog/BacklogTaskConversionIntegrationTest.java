package com.aidevos.orchestrator.backlog;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.executor.ExecutorRegistry;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanSnapshotFactory;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.PlanValidator;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import com.aidevos.orchestrator.plan.approval.PlanApprovalStore;
import com.aidevos.orchestrator.plan.run.InMemoryPlanRunRepository;
import com.aidevos.orchestrator.plan.run.PlanRunRepository;
import com.aidevos.orchestrator.planner.HermesPlanner;
import com.aidevos.orchestrator.planner.PlannerService;
import com.aidevos.orchestrator.planner.PlanningResult;
import com.aidevos.orchestrator.planner.replan.ReplanValidator;
import com.aidevos.orchestrator.project.Project;
import com.aidevos.orchestrator.project.ProjectService;
import com.aidevos.orchestrator.project.ProjectTaskService;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;
import com.aidevos.orchestrator.taskcenter.InMemoryTaskRepository;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BacklogTaskConversionIntegrationTest {
	@Test void conversionUsesFormalTaskCenterButNeverApprovesOrExecutes() {
		PlannerService planner = mock(PlannerService.class);
		PlanApprovalService approvals = mock(PlanApprovalService.class);
		PlanRunRepository planRuns = mock(PlanRunRepository.class);
		Plan plan = new Plan("plan-1", 1, "Goal", PlanStatus.DRAFT, List.of(), List.of(), null, Instant.now());
		when(planner.createPlan(any())).thenReturn(PlanningResult.success("hermes", null, plan));
		when(approvals.create(anyString(), eq(plan))).thenAnswer(invocation ->
			new PlanApprovalRequest("approval-1", invocation.getArgument(0), plan, "hash", Instant.now()));
		AuditService audit = new AuditService(new InMemoryAuditRepository());
		TaskCenterService taskCenter = spy(new TaskCenterService(planner, approvals, planRuns,
			null, audit, new InMemoryTaskRepository()));
		ProjectService projects = mock(ProjectService.class);
		WorkspaceService workspaces = mock(WorkspaceService.class);
		when(projects.getProject("project-1")).thenReturn(Optional.of(mock(Project.class)));
		Workspace workspace = new Workspace("workspace-1", "project-1", "/tmp/backlog-fixture", "main",
			WorkspaceStatus.READY, Instant.now(), Instant.now());
		when(workspaces.getWorkspace("workspace-1")).thenReturn(Optional.of(workspace));
		ProjectTaskService projectTasks = new ProjectTaskService(taskCenter, projects, workspaces);
		BacklogService backlog = new BacklogService(new InMemoryBacklogRepository(), projects,
			workspaces, projectTasks, taskCenter, audit);
		BacklogItem item = backlog.create(new CreateBacklogRequest("Implement future work", null,
			BacklogStatus.READY, BacklogPriority.HIGH, null, null, BacklogSourceType.ROADMAP,
			"docs/roadmap/README.md#future", null, List.of(), List.of()));

		BacklogConversionResult result = backlog.convertToTask(item.getBacklogItemId(),
			new ConvertBacklogToTaskRequest("Implement safely", "hermes", "project-1",
				"workspace-1", ExecutionMode.READ_ONLY));

		TaskRecord task = taskCenter.getTask(result.task().getTaskId()).orElseThrow();
		assertEquals(TaskStatus.PLANNING, task.getStatus());
		assertEquals("approval-1", task.getApprovalId());
		assertEquals(BacklogStatus.CONVERTED, result.backlogItem().getStatus());
		assertEquals(task.getTaskId(), result.backlogItem().getConvertedTaskId());
		verify(taskCenter, never()).approve(anyString(), anyString());
		verify(taskCenter, never()).execute(anyString());
		verify(taskCenter, never()).execute(anyString(), any());
		verify(approvals, never()).approve(anyString(), anyString());
	}

	@Test void recommendationBacklogReadWriteConversionPlansCoderButDoesNotApproveOrExecute() {
		PlanValidator validator = new PlanValidator();
		AgentDefinition plannerAgent = agent("planner", "mock", List.of("planning", "analysis"));
		AgentDefinition analyst = agent("analyst", "codex", List.of("analysis", "read-only"));
		AgentDefinition coder = agent("coder", "codex", List.of("coding", "git"));
		AgentManager agents = mock(AgentManager.class);
		when(agents.getAllAgents()).thenReturn(List.of(plannerAgent, analyst, coder));
		ExecutorRegistry executors = mock(ExecutorRegistry.class);
		when(executors.getTypes()).thenReturn(Set.of("mock", "codex"));
		com.aidevos.orchestrator.tool.ToolRegistry tools =
			mock(com.aidevos.orchestrator.tool.ToolRegistry.class);
		when(tools.getTools()).thenReturn(List.of());
		PlanSnapshotFactory snapshots = new PlanSnapshotFactory(agents, tools, executors);
		PlannerService planner = new PlannerService(List.of(new HermesPlanner()), validator,
			new ReplanValidator(validator), AuditService.noop(), snapshots, "v1");
		PlanApprovalService approvals = spy(new PlanApprovalService(new PlanApprovalStore(),
			validator, new ObjectMapper(), AuditService.noop()));
		TaskCenterService taskCenter = spy(new TaskCenterService(planner, approvals,
			new InMemoryPlanRunRepository(), null, AuditService.noop(),
			new InMemoryTaskRepository()));
		ProjectService projects = mock(ProjectService.class);
		WorkspaceService workspaces = mock(WorkspaceService.class);
		when(projects.getProject("project-1")).thenReturn(Optional.of(mock(Project.class)));
		when(workspaces.getWorkspace("workspace-1")).thenReturn(Optional.of(new Workspace(
			"workspace-1", "project-1", "/tmp/backlog-fixture", "main",
			WorkspaceStatus.READY, Instant.now(), Instant.now())));
		BacklogService backlog = new BacklogService(new InMemoryBacklogRepository(), projects,
			workspaces, new ProjectTaskService(taskCenter, projects, workspaces), taskCenter,
			AuditService.noop());
		BacklogItem item = backlog.create(new CreateBacklogRequest("Implement migration",
			"Implement safely", BacklogStatus.READY, BacklogPriority.CRITICAL, "project-1",
			"workspace-1", BacklogSourceType.TASK, "recommendation:R-001", null, List.of(),
			List.of("recommendation")));

		BacklogConversionResult result = backlog.convertToTask(item.getBacklogItemId(),
			new ConvertBacklogToTaskRequest("Implement migration", "hermes", "project-1",
				"workspace-1", ExecutionMode.READ_WRITE));

		TaskRecord task = result.task();
		PlanApprovalRequest approval = approvals.get(task.getApprovalId());
		var step = approval.getPlan().steps().getFirst();
		assertEquals(ExecutionMode.READ_WRITE, task.getExecutionMode());
		assertEquals(TaskStatus.PLANNING, task.getStatus());
		assertEquals("coder", step.assignment().agentName());
		assertEquals("git-diff", step.expectedArtifacts().getFirst().type());
		assertEquals("changes.patch", step.expectedArtifacts().getFirst().name());
		assertEquals(com.aidevos.orchestrator.approval.ApprovalStatus.PENDING,
			approval.getStatus());
		verify(taskCenter, never()).approve(anyString(), anyString());
		verify(taskCenter, never()).execute(anyString());
		verify(taskCenter, never()).execute(anyString(), any());
		verify(approvals, never()).approve(anyString(), anyString());
	}

	private AgentDefinition agent(String name, String executor, List<String> capabilities) {
		AgentDefinition agent = new AgentDefinition();
		agent.setName(name);
		agent.setExecutor(executor);
		agent.setCapabilities(capabilities);
		agent.setEnabled(true);
		return agent;
	}
}
