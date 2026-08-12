package com.aidevos.orchestrator.project;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import com.aidevos.orchestrator.workspace.git.GitStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Manages development projects: create, query, switch the current project and
 * archive/delete. The current project is the isolation boundary used by tasks,
 * memory and agent execution.
 */
@Service
public class ProjectService {

	private final ProjectRepository repository;
	private final AuditService auditService;
	private final GitCommandExecutor gitCommandExecutor;
	private volatile String currentProjectId;

	public ProjectService(ProjectRepository repository) {
		this(repository, AuditService.noop(), null);
	}

	public ProjectService(ProjectRepository repository, AuditService auditService) {
		this(repository, auditService, null);
	}

	@Autowired
	public ProjectService(ProjectRepository repository, AuditService auditService,
			GitCommandExecutor gitCommandExecutor) {
		this.repository = repository;
		this.auditService = auditService;
		this.gitCommandExecutor = gitCommandExecutor;
	}

	public Project createProject(CreateProjectRequest request) {
		if (request == null || isBlank(request.name()) || isBlank(request.path())) {
			throw new IllegalArgumentException("Project name and path are required");
		}
		String path = request.path().trim();
		String repositoryUrl = normalize(request.repositoryUrl());
		String defaultBranch = normalize(request.defaultBranch());
		if (gitCommandExecutor != null) {
			GitStatus status = gitCommandExecutor.status(path);
			if (status != null && !isBlank(status.getBranch())) {
				defaultBranch = status.getBranch().trim();
			}
			String originUrl = originUrl(gitCommandExecutor.listRemotes(path));
			if (originUrl != null) {
				repositoryUrl = originUrl;
			}
		}
		String projectId = "project-" + UUID.randomUUID();
		Project project = new Project(projectId, request.name().trim(), path,
			request.description(), ProjectStatus.ACTIVE, Instant.now(), Instant.now(),
			repositoryUrl, defaultBranch);
		repository.save(project);
		auditService.projectEvent(EventType.PROJECT_CREATED, projectId,
			"Project created: " + project.getName(),
			Map.of("path", project.getPath() == null ? "" : project.getPath(),
				"repositoryUrl", project.getRepositoryUrl() == null ? ""
					: project.getRepositoryUrl()));
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
			auditService.adminEvent(EventType.PROJECT_SWITCHED, "project", projectId, "USER",
				"Project switched to active: " + value.getName(), Map.of("path",
					value.getPath() == null ? "" : value.getPath()));
		});
		return project;
	}

	public Optional<Project> archive(String projectId) {
		Optional<Project> project = getProject(projectId);
		project.ifPresent(value -> {
			value.markArchived();
			repository.save(value);
			auditService.projectEvent(EventType.PROJECT_ARCHIVED, projectId,
				"Project archived: " + value.getName(), Map.of());
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

	private String normalize(String value) {
		return isBlank(value) ? null : value.trim();
	}

	private String originUrl(String remotes) {
		if (isBlank(remotes)) {
			return null;
		}
		for (String line : remotes.split("\\R")) {
			String[] fields = line.trim().split("\\s+");
			if (fields.length >= 2 && "origin".equals(fields[0])
					&& (fields.length < 3 || "(fetch)".equals(fields[2]))) {
				return normalize(fields[1]);
			}
		}
		return null;
	}
}
