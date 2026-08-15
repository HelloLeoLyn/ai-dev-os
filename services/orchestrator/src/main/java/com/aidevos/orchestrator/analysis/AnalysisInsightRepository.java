package com.aidevos.orchestrator.analysis;

import java.util.List;

public interface AnalysisInsightRepository {
	AnalysisInsightSet save(AnalysisInsightSet insight);
	AnalysisInsightSet get(String analysisId);
	AnalysisInsightSet findByTaskId(String taskId);
	AnalysisInsightSet findBySource(String taskId, String executionRecordId, String extractorVersion);
	List<AnalysisInsightSet> findByProjectId(String projectId);
	List<AnalysisInsightSet> findByStatus(AnalysisEnums.Status status);
}
