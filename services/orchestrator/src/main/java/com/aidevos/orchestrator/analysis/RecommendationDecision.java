package com.aidevos.orchestrator.analysis;

import java.time.Instant;

public record RecommendationDecision(String recommendationId, String analysisId,
		String sourceTaskId, String projectId, RecommendationStatus status,
		Instant deferUntil, String deferReason, String ignoreReason,
		String convertedBacklogItemId, long version, Instant createdAt, Instant updatedAt) {
	public RecommendationDecision transition(RecommendationStatus next, Instant until,
			String defer, String ignore, String backlogId, Instant now) {
		return new RecommendationDecision(recommendationId, analysisId, sourceTaskId, projectId,
			next, until, defer, ignore, backlogId, version + 1, createdAt, now);
	}
}
