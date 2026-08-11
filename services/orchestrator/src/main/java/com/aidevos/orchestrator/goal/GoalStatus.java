package com.aidevos.orchestrator.goal;

/**
 * Lifecycle of an autonomous goal: created, planning (decomposition through
 * the dynamic planner), running (tasks in the orchestrator pool), paused,
 * completed or failed.
 */
public enum GoalStatus {
	CREATED,
	PLANNING,
	RUNNING,
	PAUSED,
	COMPLETED,
	FAILED
}
