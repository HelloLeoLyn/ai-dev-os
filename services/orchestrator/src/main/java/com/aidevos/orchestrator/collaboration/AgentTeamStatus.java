package com.aidevos.orchestrator.collaboration;

/**
 * Lifecycle of one agent team: CREATED -> RUNNING -> COMPLETED, or
 * RUNNING -> WAITING (waiting on a handoff) and RUNNING -> FAILED.
 */
public enum AgentTeamStatus {
	CREATED,
	RUNNING,
	WAITING,
	COMPLETED,
	FAILED
}
