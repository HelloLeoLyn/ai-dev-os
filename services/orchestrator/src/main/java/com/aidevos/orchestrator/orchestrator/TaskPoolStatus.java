package com.aidevos.orchestrator.orchestrator;

/**
 * Lifecycle status of the task pool: CREATED until the first task is
 * submitted, RUNNING while tasks are queued or executing, PAUSED when the
 * orchestrator pauses, COMPLETED when every task finished.
 */
public enum TaskPoolStatus {
	CREATED,
	RUNNING,
	PAUSED,
	COMPLETED
}
