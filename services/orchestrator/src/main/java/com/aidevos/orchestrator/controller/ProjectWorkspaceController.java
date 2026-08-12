package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.project.ProjectService;
import com.aidevos.orchestrator.project.ProjectTaskService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.CreateTaskRequest;
import com.aidevos.orchestrator.workspace.CreateProjectWorkspaceRequest;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project-scoped resources: the workspaces and tasks belonging to one
 * project.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectWorkspaceController {

	private final ProjectService projectService;
	private final WorkspaceService workspaceService;
	private final ProjectTaskService projectTaskService;

	public ProjectWorkspaceController(ProjectService projectService,
			WorkspaceService workspaceService, ProjectTaskService projectTaskService) {
		this.projectService = projectService;
		this.workspaceService = workspaceService;
		this.projectTaskService = projectTaskService;
	}

	@GetMapping("/{id}/workspaces")
	public List<Workspace> workspaces(@PathVariable String id) {
		requireProject(id);
		return workspaceService.getProjectWorkspaces(id);
	}

	@PostMapping("/{id}/workspaces")
	public ResponseEntity<Workspace> createWorkspace(@PathVariable String id,
			@RequestBody CreateProjectWorkspaceRequest request) {
		var project = projectService.getProject(id)
			.orElseThrow(() -> new ResourceNotFoundException("Project", id));
		String requestedPath = request == null ? null : request.path();
		String path = requestedPath == null || requestedPath.isBlank()
			? project.getPath() : requestedPath;
		Workspace workspace = workspaceService.createProjectWorkspace(project.getProjectId(),
			path, project.getRepositoryUrl());
		return ResponseEntity.status(HttpStatus.CREATED).body(workspace);
	}

	@GetMapping("/{id}/tasks")
	public List<TaskRecord> tasks(@PathVariable String id) {
		requireProject(id);
		return projectTaskService.listTasksByProject(id);
	}

	@PostMapping("/{id}/tasks")
	public ResponseEntity<TaskRecord> createTask(@PathVariable String id,
			@RequestBody CreateTaskRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(projectTaskService.createTask(id, request));
	}

	private void requireProject(String projectId) {
		projectService.getProject(projectId)
			.orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
	}
}
