package com.aidevos.orchestrator.analysis;

import com.aidevos.orchestrator.backlog.BacklogPriority;

public record CreateRecommendationWorkItemRequest(String title, String description,
		BacklogPriority priority, String actor) { }
