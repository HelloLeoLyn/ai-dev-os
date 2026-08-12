package com.aidevos.orchestrator.project;

import java.time.Instant;
import java.util.Optional;

import com.aidevos.orchestrator.taskcenter.CreateTaskRequest;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectTaskContextServiceTest {

	private final TaskCenterService taskCenter = mock(TaskCenterService.class);
	private final ProjectService projects = mock(ProjectService.class);
	private final WorkspaceService workspaces = mock(WorkspaceService.class);
	private final ProjectTaskService service = new ProjectTaskService(taskCenter, projects, workspaces);

	@Test
	void rejectsNullAndDefaultProjectAndNullWorkspace() {
		assertThrows(IllegalArgumentException.class, () -> service.createTask(request(null, "ws-1")));
		assertThrows(IllegalArgumentException.class, () -> service.createTask(request("default", "ws-1")));
		when(projects.getProject("project-1")).thenReturn(Optional.of(project()));
		assertThrows(IllegalArgumentException.class,
			() -> service.createTask(request("project-1", null)));
	}

	@Test
	void rejectsWorkspaceOwnedByAnotherProject() {
		when(projects.getProject("project-1")).thenReturn(Optional.of(project()));
		when(workspaces.getWorkspace("ws-other")).thenReturn(Optional.of(workspace("project-2")));

		assertThrows(IllegalArgumentException.class,
			() -> service.createTask(request("project-1", "ws-other")));
	}

	@Test
	void urlProjectOverridesBodyAndPassesWorkspacePathToPlannerEntry() {
		when(projects.getProject("project-1")).thenReturn(Optional.of(project()));
		Workspace workspace = workspace("project-1");
		when(workspaces.getWorkspace("ws-1")).thenReturn(Optional.of(workspace));
		TaskRecord created = new TaskRecord("task-1", "Analyze", "Read only", "project-1",
			"ws-1", ExecutionMode.READ_ONLY);
		when(taskCenter.createTask(org.mockito.ArgumentMatchers.any(), eq("/repo")))
			.thenReturn(created);

		TaskRecord result = service.createTask("project-1", request("project-other", "ws-1"));

		assertEquals("project-1", result.getProjectId());
		verify(taskCenter).createTask(org.mockito.ArgumentMatchers.argThat(request ->
			"project-1".equals(request.projectId()) && "ws-1".equals(request.workspaceId())
				&& request.executionMode() == ExecutionMode.READ_ONLY), eq("/repo"));
	}

	private CreateTaskRequest request(String projectId, String workspaceId) {
		return new CreateTaskRequest("Analyze", "Read only", "Analyze", "hermes",
			projectId, workspaceId, ExecutionMode.READ_ONLY);
	}

	private Project project() {
		return new Project("project-1", "demo", "/repo", null, ProjectStatus.ACTIVE,
			Instant.now(), Instant.now());
	}

	private Workspace workspace(String projectId) {
		return new Workspace("ws-1", projectId, "/repo", "dev", WorkspaceStatus.READY,
			Instant.now(), Instant.now());
	}
}
