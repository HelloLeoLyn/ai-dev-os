package com.aidevos.orchestrator.remote;

/**
 * Lifecycle of one remote push triggered by a successfully committed change:
 * PENDING -> PUSHING -> SUCCESS | FAILED.
 */
public enum RemoteStatus {
	PENDING,
	PUSHING,
	SUCCESS,
	FAILED
}
