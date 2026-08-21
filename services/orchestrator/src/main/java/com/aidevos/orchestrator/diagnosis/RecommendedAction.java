package com.aidevos.orchestrator.diagnosis;

/**
 * V1 推荐动作。UI 复用现有 Retry / Replan capability（仅当语义允许时）。
 */
public enum RecommendedAction {
	RETRY,
	REPLAN,
	FIX_CONFIGURATION,
	HUMAN_INTERVENTION,
	NONE
}
