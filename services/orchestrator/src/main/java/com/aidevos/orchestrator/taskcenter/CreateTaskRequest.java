package com.aidevos.orchestrator.taskcenter;

/**
 * User request that creates a Task Center task. The 5-argument constructor is
 * kept for callers that do not bind a workspace yet.
 */
public record CreateTaskRequest(String name, String description, String goal,
		String plannerName, String projectId, String workspaceId) {

	public CreateTaskRequest(String name, String description, String goal,
			String plannerName, String projectId) {
		this(name, description, goal, plannerName, projectId, null);
	}
}
