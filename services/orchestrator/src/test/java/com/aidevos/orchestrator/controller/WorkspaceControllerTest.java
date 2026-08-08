package com.aidevos.orchestrator.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import com.aidevos.orchestrator.workspace.git.GitDiff;
import com.aidevos.orchestrator.workspace.git.GitStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class WorkspaceControllerTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private MockMvc mockMvc(WorkspaceService service) {
		return standaloneSetup(new WorkspaceController(service))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void shouldCreateWorkspace() throws Exception {
		WorkspaceService service = mock(WorkspaceService.class);
		when(service.createWorkspace("project-a", "/tmp/repo")).thenReturn(workspace());
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(post("/api/workspaces")
				.contentType("application/json")
				.content("{\"projectId\":\"project-a\",\"path\":\"/tmp/repo\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.workspaceId").value("workspace-1"))
			.andExpect(jsonPath("$.projectId").value("project-a"))
			.andExpect(jsonPath("$.status").value("READY"));

		verify(service).createWorkspace("project-a", "/tmp/repo");
	}

	@Test
	void shouldListWorkspaces() throws Exception {
		WorkspaceService service = mock(WorkspaceService.class);
		when(service.listWorkspaces()).thenReturn(List.of(workspace()));
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/workspaces"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].workspaceId").value("workspace-1"))
			.andExpect(jsonPath("$[0].path").value("/tmp/repo"));
	}

	@Test
	void shouldGetWorkspaceById() throws Exception {
		WorkspaceService service = mock(WorkspaceService.class);
		when(service.getWorkspace("workspace-1")).thenReturn(Optional.of(workspace()));
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/workspaces/workspace-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.workspaceId").value("workspace-1"))
			.andExpect(jsonPath("$.branch").value("main"));
	}

	@Test
	void shouldReturn404ForMissingWorkspace() throws Exception {
		WorkspaceService service = mock(WorkspaceService.class);
		when(service.getWorkspace("missing")).thenReturn(Optional.empty());
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/workspaces/missing"))
			.andExpect(status().isNotFound());
	}

	@Test
	void shouldReturnGitStatus() throws Exception {
		WorkspaceService service = mock(WorkspaceService.class);
		when(service.checkGitStatus("workspace-1")).thenReturn(
			new GitStatus("main", 3, 2, 0));
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/workspaces/workspace-1/git/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.branch").value("main"))
			.andExpect(jsonPath("$.modified").value(3))
			.andExpect(jsonPath("$.added").value(2))
			.andExpect(jsonPath("$.deleted").value(0));
	}

	@Test
	void shouldReturnGitDiff() throws Exception {
		WorkspaceService service = mock(WorkspaceService.class);
		when(service.getGitDiff("workspace-1")).thenReturn(
			new GitDiff(2, 5, 1, "2 files changed, 5 insertions(+), 1 deletion(-)"));
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/workspaces/workspace-1/git/diff"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.filesChanged").value(2))
			.andExpect(jsonPath("$.insertions").value(5))
			.andExpect(jsonPath("$.deletions").value(1));
	}

	private Workspace workspace() {
		return new Workspace("workspace-1", "project-a", "/tmp/repo", "main",
			WorkspaceStatus.READY, NOW, NOW);
	}
}
