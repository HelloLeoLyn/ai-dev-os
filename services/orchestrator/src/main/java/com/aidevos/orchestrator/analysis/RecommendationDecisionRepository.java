package com.aidevos.orchestrator.analysis;

import java.util.List;

public interface RecommendationDecisionRepository {
	RecommendationDecision get(String recommendationId);
	default List<RecommendationDecision> findByLegacyRecommendationId(String localRecommendationId) { return List.of(); }
	RecommendationDecision createIfAbsent(RecommendationDecision initial);
	RecommendationDecision lock(String recommendationId);
	boolean saveIfVersion(RecommendationDecision value, long expectedVersion);
}
