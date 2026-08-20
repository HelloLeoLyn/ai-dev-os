package com.aidevos.orchestrator.execution;

/**
 * The automatic response selected for a failure class. Every automatic
 * response is bounded by ExecutionLimits; L2/L3 always route to a human and
 * L4 always stops.
 */
public enum FailureResponse {
	RETRY_TOOL,
	RETRY_AI,
	REPLAN_AI,
	REQUEST_HUMAN,
	STOP
}
