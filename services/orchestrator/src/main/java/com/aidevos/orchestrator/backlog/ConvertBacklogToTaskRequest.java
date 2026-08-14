package com.aidevos.orchestrator.backlog;

import com.aidevos.orchestrator.taskcenter.ExecutionMode;

public record ConvertBacklogToTaskRequest(String goal, String plannerName, String projectId,
		String workspaceId, ExecutionMode executionMode) { }
