package com.aidevos.orchestrator.job;

public enum JobStatus {

	QUEUED,

	RUNNING,
	WAITING_APPROVAL,

	SUCCESS,

	FAILED,

	RETRY_WAIT,
	CANCELLED,
	RECOVERY_REQUIRED
}
