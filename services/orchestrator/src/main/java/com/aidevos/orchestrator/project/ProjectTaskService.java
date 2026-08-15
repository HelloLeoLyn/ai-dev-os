package com.aidevos.orchestrator.project;

import java.util.List;

import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.CreateTaskRequest;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Project-scoped task queries: lists the tasks belonging to one project
 * without touching the global task flow.
 */
@Service
public class ProjectTaskService {

	private final TaskCenterService taskCenterService;
	private final ProjectService projectService;
	private final WorkspaceService workspaceService;

	public ProjectTaskService(TaskCenterService taskCenterService) {
		this(taskCenterService, null, null);
	}

	@Autowired
	public ProjectTaskService(TaskCenterService taskCenterService, ProjectService projectService,
			WorkspaceService workspaceService) {
		this.taskCenterService = taskCenterService;
		this.projectService = projectService;
		this.workspaceService = workspaceService;
	}

	public List<TaskRecord> listTasksByProject(String projectId) {
		if (projectId == null || projectId.isBlank()) {
			return List.of();
		}
		return taskCenterService.listTasksByProject(projectId);
	}

	public TaskRecord createTask(CreateTaskRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Task request is required");
		}
		return createTask(request.projectId(), request);
	}

	public TaskRecord createTask(String projectId, CreateTaskRequest request) {
		return createTask(projectId, request, null);
	}

	public TaskRecord createTask(String projectId, CreateTaskRequest request,
			String sourceBacklogItemId) {
		if (projectService == null || workspaceService == null) {
			throw new IllegalStateException("Project task context validation is not configured");
		}
		if (projectId == null || projectId.isBlank() || "default".equals(projectId.trim())) {
			throw new IllegalArgumentException("A non-default projectId is required");
		}
		String normalizedProjectId = projectId.trim();
		projectService.getProject(normalizedProjectId)
			.orElseThrow(() -> new com.aidevos.orchestrator.common.exception.ResourceNotFoundException(
				"Project", normalizedProjectId));
		if (request == null || request.workspaceId() == null || request.workspaceId().isBlank()) {
			throw new IllegalArgumentException("workspaceId is required");
		}
		Workspace workspace = workspaceService.getWorkspace(request.workspaceId().trim())
			.orElseThrow(() -> new com.aidevos.orchestrator.common.exception.ResourceNotFoundException(
				"Workspace", request.workspaceId().trim()));
		if (!normalizedProjectId.equals(workspace.getProjectId())) {
			throw new IllegalArgumentException("Workspace does not belong to project");
		}
		CreateTaskRequest validated = new CreateTaskRequest(request.name(), request.description(),
			request.goal(), request.plannerName(), normalizedProjectId, workspace.getWorkspaceId(),
			request.executionMode() == null
				? com.aidevos.orchestrator.taskcenter.ExecutionMode.READ_ONLY
				: request.executionMode());
		return sourceBacklogItemId == null
			? taskCenterService.createTask(validated, workspace.getPath())
			: taskCenterService.createTask(validated, workspace.getPath(), sourceBacklogItemId);
	}
}
