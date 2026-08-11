package com.aidevos.orchestrator.orchestrator;

/**
 * Lifecycle status of one orchestrated task inside the task pool:
 * QUEUED -> RUNNING -> COMPLETED, with PAUSED and FAILED as interrupt/end
 * states. The runtime session drives the execution; the orchestration status
 * mirrors it at the pool level.
 */
public enum OrchestrationTaskStatus {
	QUEUED,
	RUNNING,
	PAUSED,
	COMPLETED,
	FAILED
}
