package com.aidevos.orchestrator.repair;

/**
 * Lifecycle of an automatic repair attempt:
 * PENDING -> ANALYZING -> FIXING -> VERIFYING -> SUCCESS | FAILED.
 * Transitions are driven by RepairCoordinator; retries restart at ANALYZING
 * and the loop is bounded by the unified ExecutionLimits repair ceiling.
 */
public enum RepairStatus {
	PENDING,
	ANALYZING,
	FIXING,
	VERIFYING,
	SUCCESS,
	FAILED
}
