package com.aidevos.orchestrator.workspace;

/**
 * Lifecycle status of a workspace: READY (available), LOCKED (in use by an
 * agent), CLEANUP (being cleaned up) and FAILED (unusable after an error).
 */
public enum WorkspaceStatus {
	READY,
	LOCKED,
	CLEANUP,
	FAILED
}
