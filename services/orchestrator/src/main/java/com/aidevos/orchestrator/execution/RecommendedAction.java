package com.aidevos.orchestrator.execution;

/**
 * Human-recommended action stored with a NEEDS_INTERVENTION state. The
 * scheduler never executes these automatically; they guide the human
 * takeover decision.
 */
public enum RecommendedAction {
	RETRY_MANUALLY,
	FIX_CREDENTIAL,
	CHECK_NETWORK,
	REVIEW_CODE,
	REPLAN,
	ABORT
}
