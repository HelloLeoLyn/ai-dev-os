package com.aidevos.orchestrator.analysis;

import java.util.List;
import com.aidevos.orchestrator.analysis.AnalysisEnums.Level;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;

public record Recommendation(String recommendationId, List<String> findingIds, String title,
		String rationale, Level priority, Level risk, Level benefit, List<String> scope,
		List<String> dependencies, ExecutionMode suggestedExecutionMode, boolean approvalRequired,
		List<EvidenceRef> evidenceRefs, double confidence,
		RecommendedNextAction recommendedNextAction) {
	public Recommendation {
		findingIds = findingIds == null ? List.of() : List.copyOf(findingIds);
		scope = scope == null ? List.of() : List.copyOf(scope);
		dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
		evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
		if (suggestedExecutionMode == ExecutionMode.READ_WRITE) approvalRequired = true;
	}
}
