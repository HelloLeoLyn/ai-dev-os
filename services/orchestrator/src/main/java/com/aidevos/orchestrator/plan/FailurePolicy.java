package com.aidevos.orchestrator.plan;

public enum FailurePolicy {

	STOP_PLAN,
	RETRY_STEP,
	USE_FALLBACK_AGENT,
	REQUEST_REPLAN,
	CONTINUE_OPTIONAL
}
