package com.aidevos.orchestrator.analysis;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix="aidevos.persistence", name="type", havingValue="in-memory", matchIfMissing=true)
public class InMemoryRecommendationDecisionRepository implements RecommendationDecisionRepository {
	private final Map<String, RecommendationDecision> values = new ConcurrentHashMap<>();
	@Override public RecommendationDecision get(String id) { return values.get(id); }
	@Override public List<RecommendationDecision> findByLegacyRecommendationId(String id) {
		RecommendationDecision value=values.get(id); return value==null?List.of():List.of(value);
	}
	@Override public RecommendationDecision createIfAbsent(RecommendationDecision value) {
		return values.computeIfAbsent(value.recommendationId(), ignored -> value);
	}
	@Override public RecommendationDecision lock(String id) { return get(id); }
	@Override public synchronized boolean saveIfVersion(RecommendationDecision value, long expected) {
		RecommendationDecision current=values.get(value.recommendationId());
		if (current==null || current.version()!=expected) return false;
		values.put(value.recommendationId(), value); return true;
	}
}
