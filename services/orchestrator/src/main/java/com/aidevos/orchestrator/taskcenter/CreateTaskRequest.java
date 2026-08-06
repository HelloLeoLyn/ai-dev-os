package com.aidevos.orchestrator.taskcenter;

/**
 * User request that creates a Task Center task.
 */
public record CreateTaskRequest(String name, String description, String goal,
		String plannerName, String projectId) {
}
