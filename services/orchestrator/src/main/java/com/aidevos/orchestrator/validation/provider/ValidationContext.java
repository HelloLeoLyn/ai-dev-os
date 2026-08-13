package com.aidevos.orchestrator.validation.provider;

import java.nio.file.Path;
import java.util.Map;

import com.aidevos.orchestrator.validation.ValidationCheckType;

public record ValidationContext(String validationRunId, String taskId, String projectId,
		String workspaceId, Path workspacePath, ValidationCheckType type,
		Map<String, Object> capabilities) { }
