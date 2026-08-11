package com.aidevos.orchestrator.goal;

/**
 * Lifecycle of one goal milestone (planning / implementation / verification).
 */
public enum MilestoneStatus {
	CREATED,
	RUNNING,
	COMPLETED,
	FAILED
}
