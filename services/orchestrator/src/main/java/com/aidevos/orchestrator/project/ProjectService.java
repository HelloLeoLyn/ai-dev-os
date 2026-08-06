package com.aidevos.orchestrator.project;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * Manages development projects: create, query, switch the current project and
 * archive/delete. The current project is the isolation boundary used by tasks,
 * memory and agent execution.
 */
@Service
public class ProjectService {

	private final ProjectRepository repository;
	private volatile String currentProjectId;

	public ProjectService(ProjectRepository repository) {
		this.repository = repository;
	}

	public Project createProject(CreateProjectRequest request) {
		if (request == null || isBlank(request.name()) || isBlank(request.path())) {
			throw new IllegalArgumentException("Project name and path are required");
		}
		String projectId = "project-" + UUID.randomUUID();
		Project project = new Project(projectId, request.name().trim(), request.path().trim(),
			request.description(), ProjectStatus.ACTIVE, Instant.now(), Instant.now());
		repository.save(project);
		if (currentProjectId == null) {
			currentProjectId = projectId;
		}
		return project;
	}

	public List<Project> listProjects() {
		List<Project> result = new ArrayList<>(repository.list());
		result.sort(Comparator.comparing(Project::getCreatedAt).reversed());
		return result;
	}

	public Optional<Project> getProject(String projectId) {
		if (isBlank(projectId)) {
			return Optional.empty();
		}
		return Optional.ofNullable(repository.get(projectId));
	}

	public Optional<Project> setActive(String projectId) {
		Optional<Project> project = getProject(projectId);
		project.ifPresent(value -> {
			value.markActive();
			repository.save(value);
			currentProjectId = projectId;
		});
		return project;
	}

	public Optional<Project> archive(String projectId) {
		Optional<Project> project = getProject(projectId);
		project.ifPresent(value -> {
			value.markArchived();
			repository.save(value);
			if (projectId.equals(currentProjectId)) {
				currentProjectId = null;
			}
		});
		return project;
	}

	public boolean delete(String projectId) {
		boolean removed = repository.delete(projectId);
		if (removed && projectId.equals(currentProjectId)) {
			currentProjectId = null;
		}
		return removed;
	}

	public Optional<Project> getCurrentProject() {
		return currentProjectId == null ? Optional.empty() : getProject(currentProjectId);
	}

	public String getCurrentProjectId() {
		return currentProjectId;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
