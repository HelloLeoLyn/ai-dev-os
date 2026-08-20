package com.aidevos.orchestrator.delivery;

/**
 * Failure ownership for a failed delivery stage. The pipeline does not build
 * a second retry system: it records the failure and stops; existing
 * recovery/intervention paths own the fix.
 */
public enum DeliveryFailureClass {
	TRANSIENT,
	RECOVERABLE,
	HUMAN_REQUIRED,
	FATAL
}
