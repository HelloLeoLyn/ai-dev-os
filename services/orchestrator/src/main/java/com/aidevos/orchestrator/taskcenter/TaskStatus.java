package com.aidevos.orchestrator.taskcenter;

/**
 * Lifecycle status of a Task Center task:
 * CREATED -> PLANNING -> APPROVED -> CODING -> TESTING -> COMPLETED.
 * RUNNING and SUCCESS are kept for compatibility with the legacy plan-run
 * flow; FAILED is the terminal state for both paths.
 */
public enum TaskStatus {
	CREATED,
	PLANNING,
	APPROVED,
	CODING,
	TESTING,
	RUNNING,
	SUCCESS,
	COMPLETED,
	REJECTED,
	FAILED
}
