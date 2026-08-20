package com.aidevos.orchestrator.delivery;

/**
 * Stages of the unified delivery pipeline. Deterministic stages advance
 * automatically; WAITING_APPROVAL and FAILED are terminal stops for the
 * advance loop until a human decision or a fix resumes it.
 */
public enum DeliveryStage {
	CHANGE_READY,
	VALIDATING,
	QUALITY_GATE,
	COMMITTING,
	WAITING_REMOTE_PUSH_APPROVAL,
	PUSHING,
	CREATING_PR,
	CI_CHECKING,
	DELIVERY_COMPLETE,
	WAITING_APPROVAL,
	FAILED
}
