package com.aidevos.orchestrator.execution;

public enum ExecutionAttemptStatus {

	STARTING,

	RUNNING,

	SUCCEEDED,
	FAILED,

	ABANDONED,

	RECOVERY_REQUIRED
}
