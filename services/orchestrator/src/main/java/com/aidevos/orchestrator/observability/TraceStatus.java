package com.aidevos.orchestrator.observability;

/**
 * Lifecycle status of one trace: a node or tool execution is RUNNING until
 * it succeeds or fails.
 */
public enum TraceStatus {
	RUNNING,
	SUCCESS,
	FAILED
}
