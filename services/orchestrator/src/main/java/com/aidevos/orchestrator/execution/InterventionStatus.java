package com.aidevos.orchestrator.execution;

/**
 * Intervention state of a plan run after automatic execution stops. The run
 * moves to PlanRunStatus.NEEDS_INTERVENTION and no scheduler loop continues.
 */
public enum InterventionStatus {
	NONE,
	LIMIT_REACHED,
	HUMAN_INTERVENTION_REQUIRED,
	HUMAN_GATE,
	HUMAN_REQUIRED,
	STOPPED_SYSTEM_FAILURE
}
