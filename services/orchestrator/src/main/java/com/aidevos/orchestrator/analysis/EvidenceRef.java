package com.aidevos.orchestrator.analysis;

import com.aidevos.orchestrator.analysis.AnalysisEnums.EvidenceType;

public record EvidenceRef(EvidenceType type, String ref, String label, String artifactType,
		String uri, Integer line, String contentHash) { }
