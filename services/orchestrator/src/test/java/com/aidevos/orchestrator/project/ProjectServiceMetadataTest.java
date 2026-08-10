package com.aidevos.orchestrator.project;

import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 17-B: project repository metadata (repositoryUrl / defaultBranch)
 * and the project lifecycle audit events.
 */
class ProjectServiceMetadataTest {

	private InMemoryAuditRepository auditRepository;
	private AuditService auditService;
	private ProjectService service;

	@BeforeEach
	void setUp() {
		auditRepository = new InMemoryAuditRepository();
		auditService = new AuditService(auditRepository);
		service = new ProjectService(new InMemoryProjectRepository(), auditService);
	}

	@Test
	void shouldCreateProjectWithRepositoryMetadata() {
		Project project = service.createProject(new CreateProjectRequest(
			"demo", "/srv/demo", "Demo project", "https://github.com/org/demo.git", "main"));

		assertTrue(project.getProjectId().startsWith("project-"));
		assertEquals(ProjectStatus.ACTIVE, project.getStatus());
		assertEquals("https://github.com/org/demo.git", project.getRepositoryUrl());
		assertEquals("main", project.getDefaultBranch());
		assertTrue(auditRepository.query(EventQuery.all()).stream()
			.anyMatch(event -> event.type() == EventType.PROJECT_CREATED
				&& project.getProjectId().equals(event.metadata().get("projectId"))));
	}

	@Test
	void shouldQueryProjectByIdAndList() {
		Project created = service.createProject(new CreateProjectRequest(
			"demo", "/srv/demo", "Demo"));

		Optional<Project> found = service.getProject(created.getProjectId());

		assertTrue(found.isPresent());
		assertEquals("demo", found.get().getName());
		assertEquals(1, service.listProjects().size());
	}

	@Test
	void shouldArchiveProjectAndAudit() {
		Project created = service.createProject(new CreateProjectRequest(
			"demo", "/srv/demo", "Demo"));

		Optional<Project> archived = service.archive(created.getProjectId());

		assertTrue(archived.isPresent());
		assertEquals(ProjectStatus.ARCHIVED, archived.get().getStatus());
		assertTrue(auditRepository.query(EventQuery.all()).stream()
			.anyMatch(event -> event.type() == EventType.PROJECT_ARCHIVED
				&& created.getProjectId().equals(event.metadata().get("projectId"))));
	}

	@Test
	void shouldKeepLegacyThreeArgumentRequestCompatible() {
		Project project = service.createProject(new CreateProjectRequest("demo", "/srv/demo",
			"Demo"));

		assertEquals(null, project.getRepositoryUrl());
		assertEquals(null, project.getDefaultBranch());
	}
}
