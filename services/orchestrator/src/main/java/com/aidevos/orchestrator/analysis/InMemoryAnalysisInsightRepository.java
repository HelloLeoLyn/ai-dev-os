package com.aidevos.orchestrator.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix="aidevos.persistence", name="type", havingValue="in-memory", matchIfMissing=true)
public class InMemoryAnalysisInsightRepository implements AnalysisInsightRepository {
	private final Map<String, AnalysisInsightSet> values = new LinkedHashMap<>();
	@Override public synchronized AnalysisInsightSet save(AnalysisInsightSet value) {
		AnalysisInsightSet duplicate = findBySource(value.sourceTaskId(),
			value.sourceExecutionRecordId(), value.extractorVersion());
		if (duplicate != null && !duplicate.analysisId().equals(value.analysisId())) return duplicate;
		values.put(value.analysisId(), value); return value;
	}
	@Override public synchronized AnalysisInsightSet get(String id) { return values.get(id); }
	@Override public synchronized AnalysisInsightSet findByTaskId(String id) {
		return values.values().stream().filter(v -> id.equals(v.sourceTaskId()))
			.max(java.util.Comparator.comparing(AnalysisInsightSet::updatedAt)).orElse(null);
	}
	@Override public synchronized AnalysisInsightSet findBySource(String task, String execution,
			String version) { return values.values().stream().filter(v -> task.equals(v.sourceTaskId())
			&& execution.equals(v.sourceExecutionRecordId()) && version.equals(v.extractorVersion()))
			.findFirst().orElse(null); }
	@Override public synchronized List<AnalysisInsightSet> findByProjectId(String id) {
		return values.values().stream().filter(v -> id.equals(v.projectId())).toList();
	}
	@Override public synchronized AnalysisInsightSet findByRecommendationId(String id) {
		List<AnalysisInsightSet> matches = values.values().stream().filter(v -> v.recommendations().stream()
			.anyMatch(r -> id.equals(r.recommendationId()))).toList();
		if (matches.size() > 1) throw new IllegalStateException("AMBIGUOUS_RECOMMENDATION_ID: " + id);
		return matches.isEmpty() ? null : matches.getFirst();
	}
	@Override public synchronized List<AnalysisInsightSet> findByLocalRecommendationId(String id) {
		return values.values().stream().filter(v -> v.recommendations().stream()
			.anyMatch(r -> id.equals(r.localRecommendationId()))).toList();
	}
	@Override public synchronized List<AnalysisInsightSet> findByStatus(AnalysisEnums.Status status) {
		return new ArrayList<>(values.values().stream().filter(v -> status == v.status()).toList());
	}
}
