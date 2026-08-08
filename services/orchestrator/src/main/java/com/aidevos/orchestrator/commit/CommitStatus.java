package com.aidevos.orchestrator.commit;

/**
 * Lifecycle of one git commit triggered by an approved change set:
 * PENDING -> COMMITTING -> SUCCESS | FAILED.
 */
public enum CommitStatus {
	PENDING,
	COMMITTING,
	SUCCESS,
	FAILED
}
