package com.aidevos.orchestrator.agentcoordinator;

/**
 * Lifecycle of one agent step inside a collaboration plan.
 */
public enum AgentPlanStatus {
	PENDING,
	RUNNING,
	SUCCESS,
	FAILED
}
