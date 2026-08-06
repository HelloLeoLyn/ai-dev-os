package com.aidevos.orchestrator.testagent;

/**
 * Request body for creating a test task. testType and (optionally) command
 * define what runs; taskId / executionId link the test to Task Center and
 * Execution.
 */
public record CreateTestRequest(
		String taskId,
		TestType testType,
		String command,
		String executionId,
		String projectId) {
}
