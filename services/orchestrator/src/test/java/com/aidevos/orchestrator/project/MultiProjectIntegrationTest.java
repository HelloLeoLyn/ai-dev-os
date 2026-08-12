package com.aidevos.orchestrator.project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.taskcenter.CreateTaskRequest;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.workspace.InMemoryWorkspaceRepository;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import com.aidevos.orchestrator.workspace.git.GitStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 17-B: two projects running in parallel. Project A and project B each
 * have their own task and workspace; queries stay isolated.
 */
class MultiProjectIntegrationTest {

	@TempDir
	Path tempDir;

	private ProjectService projectService;
	private WorkspaceService workspaceService;
	private TaskCenterService taskCenterService;
	private ProjectTaskService projectTaskService;

	@BeforeEach
	void setUp() {
		AuditService auditService = new AuditService(new InMemoryAuditRepository());
		projectService = new ProjectService(new InMemoryProjectRepository(), auditService);
		GitCommandExecutor gitCommandExecutor = mock(GitCommandExecutor.class);
		when(gitCommandExecutor.status(any())).thenReturn(new GitStatus("main", 0, 0, 0));
		workspaceService = new WorkspaceService(new InMemoryWorkspaceRepository(),
			gitCommandExecutor, auditService);
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
	void shouldKeepProjectsFullyIsolated() throws Exception {
		Path repoA = Files.createDirectories(tempDir.resolve("repo-a"));
		Path repoB = Files.createDirectories(tempDir.resolve("repo-b"));
		Project projectA = projectService.createProject(new CreateProjectRequest(
			"alpha", repoA.toString(), "Alpha", "https://git.example/a.git", "main"));
		Project projectB = projectService.createProject(new CreateProjectRequest(
			"beta", repoB.toString(), "Beta", "https://git.example/b.git", "main"));

		Workspace workspaceA = workspaceService.createProjectWorkspace(
			projectA.getProjectId(), repoA.toString(), "https://git.example/a.git");
		Workspace workspaceB = workspaceService.createProjectWorkspace(
			projectB.getProjectId(), repoB.toString(), "https://git.example/b.git");
		TaskRecord taskA = taskCenterService.createTask(new CreateTaskRequest(
			"功能A", "功能A", "功能A", "hermes", projectA.getProjectId(),
			workspaceA.getWorkspaceId()));
		TaskRecord taskB = taskCenterService.createTask(new CreateTaskRequest(
			"功能B", "功能B", "功能B", "hermes", projectB.getProjectId(),
			workspaceB.getWorkspaceId()));

		List<Workspace> workspacesA = workspaceService.getProjectWorkspaces(
			projectA.getProjectId());
		List<Workspace> workspacesB = workspaceService.getProjectWorkspaces(
			projectB.getProjectId());
		List<TaskRecord> tasksA = projectTaskService.listTasksByProject(
			projectA.getProjectId());
		List<TaskRecord> tasksB = projectTaskService.listTasksByProject(
			projectB.getProjectId());

		assertEquals(1, workspacesA.size());
		assertEquals(1, workspacesB.size());
		assertEquals(workspaceA.getWorkspaceId(), workspacesA.get(0).getWorkspaceId());
		assertEquals(workspaceB.getWorkspaceId(), workspacesB.get(0).getWorkspaceId());
		assertEquals(1, tasksA.size());
		assertEquals(1, tasksB.size());
		assertEquals(taskA.getTaskId(), tasksA.get(0).getTaskId());
		assertEquals(taskB.getTaskId(), tasksB.get(0).getTaskId());
		assertTrue(workspaceService.checkProjectOwnership(projectA.getProjectId(),
			workspaceA.getWorkspaceId()));
		assertTrue(!workspaceService.checkProjectOwnership(projectA.getProjectId(),
			workspaceB.getWorkspaceId()));
		assertEquals(2, projectService.listProjects().size());
	}
}
