package com.aidevos.orchestrator.orchestration;

/**
 * Lifecycle of one execution graph node: PENDING -> RUNNING -> COMPLETED,
 * or RUNNING -> FAILED which stops downstream nodes (unless a bounded repair
 * loop restarts from the loop start node).
 */
public enum ExecutionNodeStatus {
	PENDING,
	RUNNING,
	COMPLETED,
	FAILED
}
