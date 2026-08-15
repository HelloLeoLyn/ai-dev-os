package com.aidevos.orchestrator.analysis;

import com.aidevos.orchestrator.backlog.BacklogItem;

public record RecommendationWorkItemResult(boolean created, BacklogItem backlogItem) { }
