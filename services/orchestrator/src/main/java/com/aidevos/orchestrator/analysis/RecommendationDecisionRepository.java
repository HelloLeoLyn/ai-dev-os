package com.aidevos.orchestrator.analysis;

public interface RecommendationDecisionRepository {
	RecommendationDecision get(String recommendationId);
	RecommendationDecision createIfAbsent(RecommendationDecision initial);
	RecommendationDecision lock(String recommendationId);
	boolean saveIfVersion(RecommendationDecision value, long expectedVersion);
}
