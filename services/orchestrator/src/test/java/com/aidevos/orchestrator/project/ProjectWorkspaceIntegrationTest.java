package com.aidevos.orchestrator.project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.taskcenter.CreateTaskRequest;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.workspace.InMemoryWorkspaceRepository;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.git.ProcessGitCommandExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 17-B: Project -> Workspace -> Task chain. A workspace and a task are
 * both bound to the same project and queryable through the project scope.
 */
class ProjectWorkspaceIntegrationTest {

	@TempDir
	Path tempDir;

	private ProjectService projectService;
	private WorkspaceService workspaceService;
	private TaskCenterService taskCenterService;
	private ProjectTaskService projectTaskService;
	private InMemoryAuditRepository auditRepository;

	@BeforeEach
	void setUp() throws Exception {
		auditRepository = new InMemoryAuditRepository();
		AuditService auditService = new AuditService(auditRepository);
		projectService = new ProjectService(new InMemoryProjectRepository(), auditService);
		workspaceService = new WorkspaceService(new InMemoryWorkspaceRepository(),
			new ProcessGitCommandExecutor(new CommandExecutor()), auditService);
		com.aidevos.orchestrator.planner.PlannerService plannerService = mock(
			com.aidevos.orchestrator.planner.PlannerService.class);
		com.aidevos.orchestrator.plan.approval.PlanApprovalService approvalService = mock(
			com.aidevos.orchestrator.plan.approval.PlanApprovalService.class);
		com.aidevos.orchestrator.plan.run.PlanRunRepository planRunRepository = mock(
			com.aidevos.orchestrator.plan.run.PlanRunRepository.class);
		taskCenterService = new TaskCenterService(plannerService, approvalService,
			planRunRepository);
		projectTaskService = new ProjectTaskService(taskCenterService);
		when(plannerService.createPlan(any(
			com.aidevos.orchestrator.planner.PlanningRequest.class))).thenReturn(
				com.aidevos.orchestrator.planner.PlanningResult.success("hermes", null,
					new com.aidevos.orchestrator.plan.Plan("plan-1", 1, "goal",
						com.aidevos.orchestrator.plan.PlanStatus.DRAFT, List.of(), List.of(),
						null, java.time.Instant.parse("2026-08-01T00:00:00Z"))));
	}

	@Test
	void shouldBindWorkspaceAndTaskToProject() throws Exception {
		Files.createDirectories(tempDir.resolve("repo"));
		Project project = projectService.createProject(new CreateProjectRequest(
			"demo", tempDir.resolve("repo").toString(), "Demo",
			"https://github.com/org/demo.git", "main"));

		Workspace workspace = workspaceService.createProjectWorkspace(project.getProjectId(),
			tempDir.resolve("repo").toString(), "https://github.com/org/demo.git");
		TaskRecord task = taskCenterService.createTask(new CreateTaskRequest(
			"实现功能", "实现功能", "实现功能", "hermes", project.getProjectId(), workspace.getWorkspaceId()));

		assertEquals(project.getProjectId(), workspace.getProjectId());
		assertEquals("https://github.com/org/demo.git", workspace.getRepositoryUrl());
		assertEquals(project.getProjectId(), task.getProjectId());
		assertEquals(1, workspaceService.getProjectWorkspaces(project.getProjectId()).size());
		assertEquals(1, projectTaskService.listTasksByProject(project.getProjectId()).size());
		assertTrue(workspaceService.checkProjectOwnership(project.getProjectId(),
			workspace.getWorkspaceId()));
		assertTrue(auditRepository.query(EventQuery.all()).stream()
			.anyMatch(event -> event.type() == EventType.PROJECT_WORKSPACE_CREATED
				&& project.getProjectId().equals(event.metadata().get("projectId"))
				&& workspace.getWorkspaceId().equals(event.metadata().get("workspaceId"))));
	}

	@Test
	void shouldRequireProjectIdForWorkspaceCreation() {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
			() -> workspaceService.createProjectWorkspace("", tempDir.toString(), null));
	}
}
