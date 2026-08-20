package com.aidevos.orchestrator.execution.system;

import java.util.Map;

/**
 * Read-only context a SYSTEM_STEP executor needs: the task/run/step/attempt
 * correlation ids, the workspace and the step parameters. No agent, model or
 * LLM context is ever exposed here.
 */
public record SystemActionContext(String taskId, String projectId, String planRunId,
		String stepRunId, String attemptId, String workspacePath,
		Map<String, Object> parameters) {
}
