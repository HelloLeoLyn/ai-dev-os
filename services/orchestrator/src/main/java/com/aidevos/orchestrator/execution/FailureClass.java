package com.aidevos.orchestrator.execution;

/**
 * Deterministic failure classification. Only UNKNOWN and CODE_LOGIC_ERROR are
 * routed to AI diagnosis; every other class is handled by the deterministic
 * execution path without calling an LLM.
 */
public enum FailureClass {
	USAGE_LIMIT,
	CREDENTIAL_MISSING,
	BUILD_FAILED,
	TEST_FAILED,
	NETWORK_ERROR,
	HEALTH_CHECK_FAILED,
	GIT_CONFLICT,
	APPROVAL_REQUIRED,
	MODEL_NOT_FOUND,
	PROVIDER_DISABLED,
	EXECUTOR_FAILED,
	CODE_LOGIC_ERROR,
	UNKNOWN,
	QUALITY_GATE_APPROVAL,
	AMBIGUOUS_STATE,
	PERMISSION_DENIED,
	REMOTE_AUTHORITY_REQUIRED,
	STATE_CORRUPTION,
	DATABASE_UNAVAILABLE,
	UNKNOWN_FATAL
}
