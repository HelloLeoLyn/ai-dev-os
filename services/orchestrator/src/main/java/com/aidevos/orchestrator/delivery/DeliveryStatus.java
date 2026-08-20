package com.aidevos.orchestrator.delivery;

/**
 * Lifecycle status of the whole delivery pipeline aggregate: RUNNING while
 * deterministic stages are advancing, WAITING_APPROVAL at a human gate,
 * COMPLETE when the delivery reached the terminal stage, FAILED when a stage
 * failed and needs a fix before advance() can continue.
 */
public enum DeliveryStatus {
	RUNNING,
	WAITING_APPROVAL,
	COMPLETE,
	FAILED
}
