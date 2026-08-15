package com.aidevos.orchestrator.analysis;

import java.time.Instant;
import java.util.List;
import com.aidevos.orchestrator.analysis.AnalysisEnums.ExtractorType;
import com.aidevos.orchestrator.analysis.AnalysisEnums.Status;

public record AnalysisInsightSet(String analysisId, String sourceTaskId,
		String sourceExecutionRecordId, String projectId, String workspaceId,
		List<EvidenceRef> sourceArtifactRefs, ExtractorType extractorType,
		String extractorVersion, String schemaVersion, Status status, String errorCode,
		String errorMessage, String contentFingerprint, List<Finding> findings,
		List<Recommendation> recommendations, Instant createdAt, Instant updatedAt) {
	public AnalysisInsightSet {
		sourceArtifactRefs = sourceArtifactRefs == null ? List.of() : List.copyOf(sourceArtifactRefs);
		findings = findings == null ? List.of() : List.copyOf(findings);
		recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
	}
	public AnalysisInsightSet withStatus(Status next, String code, String message, Instant time) {
		return new AnalysisInsightSet(analysisId, sourceTaskId, sourceExecutionRecordId, projectId,
			workspaceId, sourceArtifactRefs, extractorType, extractorVersion, schemaVersion, next,
			code, message, contentFingerprint, findings, recommendations, createdAt, time);
	}
}
