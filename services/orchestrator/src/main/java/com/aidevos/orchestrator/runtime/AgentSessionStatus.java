package com.aidevos.orchestrator.runtime;

/**
 * Lifecycle of one agent runtime session:
 * CREATED -> RUNNING -> COMPLETED, or RUNNING -> PAUSED -> RUNNING,
 * or RUNNING/PAUSED -> STOPPED and RUNNING -> FAILED (terminal).
 */
public enum AgentSessionStatus {
	CREATED,
	RUNNING,
	PAUSED,
	COMPLETED,
	FAILED,
	STOPPED
}
