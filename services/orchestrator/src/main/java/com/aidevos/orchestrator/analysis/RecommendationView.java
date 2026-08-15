package com.aidevos.orchestrator.analysis;

import java.time.Instant;
import java.util.List;
import com.aidevos.orchestrator.analysis.AnalysisEnums.Level;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;

public record RecommendationView(String recommendationId, String localRecommendationId, String sourceTaskId,
		String sourceExecutionRecordId, String title, String rationale, Level priority,
		Level risk, Level benefit, double confidence, List<String> scope,
		List<String> dependencies, ExecutionMode suggestedExecutionMode,
		boolean approvalRequired, List<Finding> findings, List<EvidenceRef> evidenceRefs,
		RecommendedNextAction recommendedNextAction, RecommendationStatus status,
		Instant deferUntil, String deferReason, String ignoreReason,
	String convertedBacklogItemId, Instant updatedAt) {
	public RecommendationView(String recommendationId, String sourceTaskId, String sourceExecutionRecordId,
			String title, String rationale, Level priority, Level risk, Level benefit, double confidence,
			List<String> scope, List<String> dependencies, ExecutionMode suggestedExecutionMode,
			boolean approvalRequired, List<Finding> findings, List<EvidenceRef> evidenceRefs,
			RecommendedNextAction recommendedNextAction, RecommendationStatus status, Instant deferUntil,
			String deferReason, String ignoreReason, String convertedBacklogItemId, Instant updatedAt) {
		this(recommendationId, recommendationId, sourceTaskId, sourceExecutionRecordId, title, rationale, priority,
			risk, benefit, confidence, scope, dependencies, suggestedExecutionMode, approvalRequired, findings,
			evidenceRefs, recommendedNextAction, status, deferUntil, deferReason, ignoreReason,
			convertedBacklogItemId, updatedAt);
	}
}
