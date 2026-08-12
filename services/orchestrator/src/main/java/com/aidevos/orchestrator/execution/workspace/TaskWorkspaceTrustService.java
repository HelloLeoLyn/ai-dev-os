package com.aidevos.orchestrator.execution.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.project.ProjectService;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/** Resolves the single registered workspace trusted for one Task execution. */
@Service
public class TaskWorkspaceTrustService {

	private final TaskCenterService taskCenterService;
	private final ProjectService projectService;
	private final WorkspaceService workspaceService;

	public TaskWorkspaceTrustService(@Lazy TaskCenterService taskCenterService,
			ProjectService projectService, WorkspaceService workspaceService) {
		this.taskCenterService = taskCenterService;
		this.projectService = projectService;
		this.workspaceService = workspaceService;
	}

	public Path requireTrustedWorkspace(ExecutionContext context, Path executionPath) {
		String taskId = required(context.getTaskId(), "Task ID");
		TaskRecord task = taskCenterService.getTask(taskId)
			.orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
		String taskProjectId = required(task.getProjectId(), "Task projectId");
		String contextProjectId = required(context.getProjectId(), "Execution projectId");
		if (!taskProjectId.equals(contextProjectId)) {
			throw new IllegalArgumentException("Execution project does not match Task project");
		}
		projectService.getProject(taskProjectId)
			.orElseThrow(() -> new IllegalArgumentException("Task project not found: " + taskProjectId));

		String taskWorkspaceId = required(task.getWorkspaceId(), "Task workspaceId");
		String contextWorkspaceId = required(metadataString(context, "workspaceId"),
			"Execution workspaceId");
		if (!taskWorkspaceId.equals(contextWorkspaceId)) {
			throw new IllegalArgumentException("Execution workspace does not match Task workspace");
		}
		Workspace workspace = workspaceService.getWorkspace(taskWorkspaceId)
			.orElseThrow(() -> new IllegalArgumentException("Task workspace not found: " + taskWorkspaceId));
		if (!taskProjectId.equals(workspace.getProjectId())) {
			throw new IllegalArgumentException("Workspace does not belong to Task project");
		}

		Path registeredPath = realDirectory(workspace.getPath(), "Registered workspace");
		if (!executionPath.equals(registeredPath)) {
			throw new IllegalArgumentException("Execution workspace path does not match registered workspace");
		}
		return registeredPath;
	}

	private String metadataString(ExecutionContext context, String key) {
		Object value = context.getMetadata().get(key);
		return value instanceof String text ? text : null;
	}

	private String required(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + " is required for Task workspace trust");
		}
		return value.trim();
	}

	private Path realDirectory(String value, String label) {
		try {
			Path path = Path.of(value).toRealPath();
			if (!Files.isDirectory(path)) {
				throw new IllegalArgumentException(label + " is not a directory: " + path);
			}
			return path;
		}
		catch (IOException exception) {
			throw new IllegalArgumentException(label + " does not exist: " + value, exception);
		}
	}
}
