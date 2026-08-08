package com.aidevos.orchestrator.change;

/**
 * Lifecycle of one AI-generated change set:
 * CREATED -> REVIEWING -> APPROVED | REJECTED, with COMMITTED reserved for a
 * later phase (commit support is intentionally not implemented here).
 */
public enum ChangeStatus {
	CREATED,
	REVIEWING,
	APPROVED,
	REJECTED,
	COMMITTED
}
