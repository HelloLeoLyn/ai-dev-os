package com.aidevos.orchestrator.backlog;

import java.util.List;
import com.aidevos.orchestrator.analysis.AnalysisEnums.Level;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;

public record BacklogRecommendationContext(String recommendationId, String analysisId,
		String sourceTaskId, String goal, List<String> acceptanceCriteria, Level risk,
		List<String> scope, ExecutionMode suggestedExecutionMode, boolean approvalRequired) {
	public BacklogRecommendationContext {
		acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
		scope = scope == null ? List.of() : List.copyOf(scope);
		if (suggestedExecutionMode == ExecutionMode.READ_WRITE) approvalRequired = true;
	}
}
