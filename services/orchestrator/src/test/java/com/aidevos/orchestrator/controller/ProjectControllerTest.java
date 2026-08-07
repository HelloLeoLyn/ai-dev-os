package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.project.CreateProjectRequest;
import com.aidevos.orchestrator.project.Project;
import com.aidevos.orchestrator.project.ProjectService;
import com.aidevos.orchestrator.project.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ProjectControllerTest {

	private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");

	@Test
	void shouldCreateProject() throws Exception {
		ProjectService service = mock(ProjectService.class);
		when(service.createProject(any(CreateProjectRequest.class))).thenReturn(project());
		MockMvc mockMvc = standaloneSetup(new ProjectController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(post("/api/projects")
				.contentType("application/json")
				.content("{\"name\":\"AI Dev OS\",\"path\":\"/workspace/ai-dev-os\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.projectId").value("project-1"))
			.andExpect(jsonPath("$.name").value("AI Dev OS"))
			.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void shouldReturn400WhenCreateInvalid() throws Exception {
		ProjectService service = mock(ProjectService.class);
		when(service.createProject(any(CreateProjectRequest.class)))
			.thenThrow(new IllegalArgumentException("Project name and path are required"));
		MockMvc mockMvc = standaloneSetup(new ProjectController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(post("/api/projects")
				.contentType("application/json")
				.content("{\"name\":\"\",\"path\":\"\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void shouldListProjects() throws Exception {
		ProjectService service = mock(ProjectService.class);
		when(service.listProjects()).thenReturn(List.of(project()));
		MockMvc mockMvc = standaloneSetup(new ProjectController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(get("/api/projects"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].projectId").value("project-1"))
			.andExpect(jsonPath("$[0].status").value("ACTIVE"));
	}

	@Test
	void shouldGetProjectById() throws Exception {
		ProjectService service = mock(ProjectService.class);
		when(service.getProject("project-1")).thenReturn(java.util.Optional.of(project()));
		MockMvc mockMvc = standaloneSetup(new ProjectController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(get("/api/projects/project-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.projectId").value("project-1"))
			.andExpect(jsonPath("$.path").value("/workspace/ai-dev-os"));
	}

	@Test
	void shouldReturn404WhenProjectMissing() throws Exception {
		ProjectService service = mock(ProjectService.class);
		when(service.getProject("missing")).thenReturn(java.util.Optional.empty());
		MockMvc mockMvc = standaloneSetup(new ProjectController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(get("/api/projects/missing"))
			.andExpect(status().isNotFound());
	}

	@Test
	void shouldSetProjectActive() throws Exception {
		ProjectService service = mock(ProjectService.class);
		Project project = project();
		when(service.setActive("project-1")).thenReturn(java.util.Optional.of(project));
		MockMvc mockMvc = standaloneSetup(new ProjectController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(post("/api/projects/project-1/active"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void shouldArchiveProject() throws Exception {
		ProjectService service = mock(ProjectService.class);
		Project project = project();
		project.markArchived();
		when(service.archive("project-1")).thenReturn(java.util.Optional.of(project));
		MockMvc mockMvc = standaloneSetup(new ProjectController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(post("/api/projects/project-1/archive"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ARCHIVED"));
	}

	@Test
	void shouldReturn404WhenTogglingMissingProject() throws Exception {
		ProjectService service = mock(ProjectService.class);
		when(service.setActive("missing")).thenReturn(java.util.Optional.empty());
		when(service.archive("missing")).thenReturn(java.util.Optional.empty());
		MockMvc mockMvc = standaloneSetup(new ProjectController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(post("/api/projects/missing/active"))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/projects/missing/archive"))
			.andExpect(status().isNotFound());
	}

	private Project project() {
		return new Project("project-1", "AI Dev OS", "/workspace/ai-dev-os",
			"main platform", ProjectStatus.ACTIVE, NOW, NOW);
	}
}
