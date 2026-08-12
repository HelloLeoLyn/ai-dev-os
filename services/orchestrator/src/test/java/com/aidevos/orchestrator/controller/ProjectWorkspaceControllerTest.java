package com.aidevos.orchestrator.controller;

import java.time.Instant;
import java.util.Optional;

import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;
import com.aidevos.orchestrator.project.Project;
import com.aidevos.orchestrator.project.ProjectService;
import com.aidevos.orchestrator.project.ProjectStatus;
import com.aidevos.orchestrator.project.ProjectTaskService;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ProjectWorkspaceControllerTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	@Test
	void shouldUseProjectPathAndBindUrlProjectId() throws Exception {
		ProjectService projectService = mock(ProjectService.class);
		WorkspaceService workspaceService = mock(WorkspaceService.class);
		Project project = project();
		Workspace workspace = workspace(project.getPath());
		when(projectService.getProject("project-1")).thenReturn(Optional.of(project));
		when(workspaceService.createProjectWorkspace("project-1", "/repo/demo",
			"https://example/demo.git")).thenReturn(workspace);

		mockMvc(projectService, workspaceService).perform(post("/api/projects/project-1/workspaces")
				.contentType("application/json").content("{}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.projectId").value("project-1"))
			.andExpect(jsonPath("$.path").value("/repo/demo"))
			.andExpect(jsonPath("$.branch").value("dev"));

		verify(workspaceService).createProjectWorkspace("project-1", "/repo/demo",
			"https://example/demo.git");
	}

	@Test
	void shouldAllowExplicitExistingPath() throws Exception {
		ProjectService projectService = mock(ProjectService.class);
		WorkspaceService workspaceService = mock(WorkspaceService.class);
		when(projectService.getProject("project-1")).thenReturn(Optional.of(project()));
		when(workspaceService.createProjectWorkspace("project-1", "/repo/alternate",
			"https://example/demo.git")).thenReturn(workspace("/repo/alternate"));

		mockMvc(projectService, workspaceService).perform(post("/api/projects/project-1/workspaces")
				.contentType("application/json").content("{\"path\":\"/repo/alternate\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.projectId").value("project-1"))
			.andExpect(jsonPath("$.path").value("/repo/alternate"));
	}

	@Test
	void shouldReturn404WhenProjectDoesNotExist() throws Exception {
		ProjectService projectService = mock(ProjectService.class);
		WorkspaceService workspaceService = mock(WorkspaceService.class);
		when(projectService.getProject("missing")).thenReturn(Optional.empty());

		mockMvc(projectService, workspaceService).perform(post("/api/projects/missing/workspaces")
				.contentType("application/json").content("{}"))
			.andExpect(status().isNotFound());
	}

	private MockMvc mockMvc(ProjectService projectService, WorkspaceService workspaceService) {
		return standaloneSetup(new ProjectWorkspaceController(projectService, workspaceService,
			mock(ProjectTaskService.class)))
			.setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	private Project project() {
		return new Project("project-1", "demo", "/repo/demo", null, ProjectStatus.ACTIVE,
			NOW, NOW, "https://example/demo.git", "dev");
	}

	private Workspace workspace(String path) {
		return new Workspace("workspace-1", "project-1", path, "dev",
			WorkspaceStatus.READY, NOW, NOW);
	}
}
