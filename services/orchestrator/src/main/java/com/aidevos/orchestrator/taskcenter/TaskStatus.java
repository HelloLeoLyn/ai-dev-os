package com.aidevos.orchestrator.taskcenter;

/**
 * Lifecycle status of a Task Center task:
 * CREATED -> PLANNING -> APPROVED -> RUNNING -> SUCCESS / FAILED.
 */
public enum TaskStatus {
	CREATED,
	PLANNING,
	APPROVED,
	RUNNING,
	SUCCESS,
	FAILED
}
