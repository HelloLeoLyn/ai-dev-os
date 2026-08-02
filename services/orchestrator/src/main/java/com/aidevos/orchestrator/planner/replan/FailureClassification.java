package com.aidevos.orchestrator.planner.replan;

public enum FailureClassification {
	TRANSIENT,
	AGENT_UNAVAILABLE,
	TOOL_ERROR,
	ARTIFACT_MISSING,
	VALIDATION_FAILED,
	PLAN_INVALID,
	USER_REQUIRED_CHANGE,
	UNKNOWN
}
