package com.aidevos.orchestrator.taskcenter;

/**
 * User request that creates a Task Center task. The 5-argument constructor is
 * kept for callers that do not bind a workspace yet. requestedModelId is an
 * optional registry-backed model selection; when blank/null the task resolves
 * through the Auto (agent default) path and never silently falls back to a
 * CLI default provider.
 */
public record CreateTaskRequest(String name, String description, String goal,
		String plannerName, String projectId, String workspaceId, ExecutionMode executionMode,
		String requestedModelId) {

	public CreateTaskRequest(String name, String description, String goal,
			String plannerName, String projectId, String workspaceId, ExecutionMode executionMode) {
		this(name, description, goal, plannerName, projectId, workspaceId, executionMode, null);
	}

	public CreateTaskRequest(String name, String description, String goal,
			String plannerName, String projectId, String workspaceId) {
		this(name, description, goal, plannerName, projectId, workspaceId,
			ExecutionMode.READ_WRITE);
	}

	public CreateTaskRequest(String name, String description, String goal,
			String plannerName, String projectId) {
		this(name, description, goal, plannerName, projectId, null,
			ExecutionMode.READ_WRITE);
	}
}
