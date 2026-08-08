package com.aidevos.orchestrator.repair;

/**
 * Lifecycle of an automatic repair attempt:
 * PENDING -> ANALYZING -> FIXING -> VERIFYING -> SUCCESS | FAILED.
 * Transitions are driven by RepairCoordinator; retries restart at ANALYZING
 * and the loop is bounded by RepairPolicy.MAX_RETRY.
 */
public enum RepairStatus {
	PENDING,
	ANALYZING,
	FIXING,
	VERIFYING,
	SUCCESS,
	FAILED
}
