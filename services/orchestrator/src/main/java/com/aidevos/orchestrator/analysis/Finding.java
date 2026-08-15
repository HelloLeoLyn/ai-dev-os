package com.aidevos.orchestrator.analysis;

import java.util.List;
import com.aidevos.orchestrator.analysis.AnalysisEnums.Level;

public record Finding(String findingId, String title, String summary, String category,
		Level severity, double confidence, List<String> scope, List<EvidenceRef> evidenceRefs) {
	public Finding {
		scope = scope == null ? List.of() : List.copyOf(scope);
		evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
	}
}
