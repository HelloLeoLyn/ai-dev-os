package com.aidevos.orchestrator.plan.run;

import java.time.Instant;

public class StepAttempt {

	private final String id;
	private final int number;
	private final Instant createdAt;
	private StepRunStatus status;
	private String jobId;
	private String executionRecordId;
	private String error;
	private Instant completedAt;

	public StepAttempt(String id, int number, Instant createdAt) {
		this.id = id;
		this.number = number;
		this.createdAt = createdAt;
		this.status = StepRunStatus.RUNNING;
	}
	public static StepAttempt restore(String id, int number, Instant createdAt,
			StepRunStatus status, String jobId, String executionRecordId, String error,
			Instant completedAt) {
		StepAttempt value = new StepAttempt(id, number, createdAt);
		value.status=status; value.jobId=jobId; value.executionRecordId=executionRecordId;
		value.error=error; value.completedAt=completedAt;
		return value;
	}

	public synchronized void bindJob(String submittedJobId) { jobId = submittedJobId; }
	public synchronized void markWaitingApproval() { status = StepRunStatus.WAITING_APPROVAL; }
	public synchronized void markRunning() { status = StepRunStatus.RUNNING; }
	public synchronized void markSuccess(String recordId, Instant time) {
		status = StepRunStatus.SUCCESS;
		executionRecordId = recordId;
		completedAt = time;
	}
	public synchronized void markFailed(String recordId, String message, Instant time) {
		status = StepRunStatus.FAILED;
		executionRecordId = recordId;
		error = message;
		completedAt = time;
	}

	public String getId() { return id; }
	public int getNumber() { return number; }
	public Instant getCreatedAt() { return createdAt; }
	public synchronized StepRunStatus getStatus() { return status; }
	public synchronized String getJobId() { return jobId; }
	public synchronized String getExecutionRecordId() { return executionRecordId; }
	public synchronized String getError() { return error; }
	public synchronized Instant getCompletedAt() { return completedAt; }
}
