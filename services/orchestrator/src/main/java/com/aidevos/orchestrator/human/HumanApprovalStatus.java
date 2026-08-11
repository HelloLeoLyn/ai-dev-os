package com.aidevos.orchestrator.human;

/**
 * Lifecycle of one human approval request:
 * PENDING -> APPROVED or REJECTED, or PENDING -> CANCELLED.
 */
public enum HumanApprovalStatus {
	PENDING,
	APPROVED,
	REJECTED,
	CANCELLED
}
