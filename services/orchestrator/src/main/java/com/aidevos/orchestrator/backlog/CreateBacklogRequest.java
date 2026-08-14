package com.aidevos.orchestrator.backlog;

import java.util.List;

public record CreateBacklogRequest(String title, String description, BacklogStatus status,
		BacklogPriority priority, String projectId, String workspaceId,
		BacklogSourceType sourceType, String sourceReference, String blockedReason,
		List<String> dependsOn, List<String> tags) { }
