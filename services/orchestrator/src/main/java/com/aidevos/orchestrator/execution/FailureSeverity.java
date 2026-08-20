package com.aidevos.orchestrator.execution;

/**
 * Failure severity ladder. L0 recovers by retrying the tool, L1 is repairable
 * by an AI pass, L2 needs a human decision, L3 requires human action and L4 is
 * a system-level failure that must stop immediately.
 */
public enum FailureSeverity {
	L0_RECOVERABLE,
	L1_AI_RECOVERABLE,
	L2_HUMAN_DECISION,
	L3_HUMAN_REQUIRED,
	L4_SYSTEM_FAILURE
}
