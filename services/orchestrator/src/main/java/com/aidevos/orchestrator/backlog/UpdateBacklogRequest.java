package com.aidevos.orchestrator.backlog;

import java.util.List;

public record UpdateBacklogRequest(String title, String description, BacklogPriority priority,
		String projectId, String workspaceId, BacklogSourceType sourceType,
		String sourceReference, List<String> dependsOn, List<String> tags) { }
