package com.aidevos.orchestrator.project;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectServiceTest {

	private ProjectService service;

	@BeforeEach
	void setUp() {
		service = new ProjectService(new InMemoryProjectRepository());
	}

	@Test
	void shouldCreateProjectAsActiveAndCurrent() {
		Project project = service.createProject(
			new CreateProjectRequest("AI Dev OS", "/workspace/ai-dev-os", "main platform"));

		assertTrue(project.getProjectId().startsWith("project-"));
		assertEquals(ProjectStatus.ACTIVE, project.getStatus());
		assertEquals("AI Dev OS", project.getName());
		assertEquals("/workspace/ai-dev-os", project.getPath());
		assertEquals(project.getProjectId(), service.getCurrentProjectId());
		assertEquals(Optional.of(project.getProjectId()),
			service.getCurrentProject().map(Project::getProjectId));
	}

	@Test
	void shouldRejectProjectWithoutNameOrPath() {
		assertThrows(IllegalArgumentException.class,
			() -> service.createProject(new CreateProjectRequest(null, "/tmp", null)));
		assertThrows(IllegalArgumentException.class,
			() -> service.createProject(new CreateProjectRequest("name", " ", null)));
	}

	@Test
	void shouldListProjectsNewestFirst() {
		Project first = service.createProject(
			new CreateProjectRequest("First", "/p/first", null));
		Project second = service.createProject(
			new CreateProjectRequest("Second", "/p/second", null));

		List<Project> projects = service.listProjects();

		assertEquals(2, projects.size());
		assertEquals(second.getProjectId(), projects.getFirst().getProjectId());
		assertEquals(first.getProjectId(), projects.get(1).getProjectId());
	}

	@Test
	void shouldGetProjectById() {
		Project created = service.createProject(
			new CreateProjectRequest("Demo", "/p/demo", "demo project"));

		assertEquals(Optional.of(created.getProjectId()),
			service.getProject(created.getProjectId()).map(Project::getProjectId));
		assertTrue(service.getProject("missing").isEmpty());
	}

	@Test
	void shouldSwitchCurrentProject() {
		Project first = service.createProject(new CreateProjectRequest("First", "/p/first", null));
		Project second = service.createProject(new CreateProjectRequest("Second", "/p/second", null));

		assertEquals(first.getProjectId(), service.getCurrentProjectId());
		service.setActive(second.getProjectId());

		assertEquals(second.getProjectId(), service.getCurrentProjectId());
		assertEquals(ProjectStatus.ACTIVE, service.getProject(second.getProjectId())
			.orElseThrow().getStatus());
		assertTrue(service.setActive("missing").isEmpty());
	}

	@Test
	void shouldArchiveProjectAndClearCurrent() {
		Project project = service.createProject(new CreateProjectRequest("Demo", "/p/demo", null));

		service.archive(project.getProjectId());

		assertEquals(ProjectStatus.ARCHIVED,
			service.getProject(project.getProjectId()).orElseThrow().getStatus());
		assertTrue(service.getCurrentProject().isEmpty());
		assertTrue(service.archive("missing").isEmpty());
	}

	@Test
	void shouldDeleteProject() {
		Project project = service.createProject(new CreateProjectRequest("Demo", "/p/demo", null));

		assertTrue(service.delete(project.getProjectId()));
		assertTrue(service.getProject(project.getProjectId()).isEmpty());
		assertFalse(service.delete(project.getProjectId()));
	}
}
