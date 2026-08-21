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
	FAILED,
	// V1-FINAL-CLOSEOUT：用户主动取消的非终态任务终态（重复 Cancel 幂等）
	CANCELLED
}
