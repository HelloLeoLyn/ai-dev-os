package com.aidevos.orchestrator.analysis;

import java.util.List;
import com.aidevos.orchestrator.analysis.AnalysisEnums.EstimatedComplexity;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;

public record RecommendedNextAction(String actionId, String title, String description, String goal,
		List<String> acceptanceCriteria, List<String> scope, List<String> dependencies,
		ExecutionMode suggestedExecutionMode, boolean approvalRequired,
		EstimatedComplexity estimatedComplexity) {
	public RecommendedNextAction {
		acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
		scope = scope == null ? List.of() : List.copyOf(scope);
		dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
		if (suggestedExecutionMode == ExecutionMode.READ_WRITE) approvalRequired = true;
	}
}
