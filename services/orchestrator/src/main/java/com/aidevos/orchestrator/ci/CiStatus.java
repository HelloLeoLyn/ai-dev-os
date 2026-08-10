package com.aidevos.orchestrator.ci;

/**
 * Lifecycle of one CI run associated with a pull request: PENDING -> RUNNING
 * -> SUCCESS | FAILED | CANCELLED. This phase only observes status; it never
 * triggers repairs or modifies code.
 */
public enum CiStatus {
	PENDING,
	RUNNING,
	SUCCESS,
	FAILED,
	CANCELLED
}
