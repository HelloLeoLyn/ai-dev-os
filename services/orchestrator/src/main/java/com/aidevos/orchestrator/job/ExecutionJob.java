package com.aidevos.orchestrator.job;

import java.time.Instant;

import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.model.TaskDefinition;

public class ExecutionJob {

	private final String id;
	private final String taskId;
	private final TaskDefinition taskSnapshot;
	private final Instant createdAt;
	private volatile JobStatus status;
	private volatile Instant startedAt;
	private volatile Instant completedAt;
	private volatile ExecutionResult result;
	private volatile String executionRecordId;
	private volatile String resultSummary;
	private volatile String errorMessage;

	public ExecutionJob(String id, TaskDefinition taskSnapshot) {
		this.id = id;
		this.taskId = taskSnapshot.getId();
		this.taskSnapshot = taskSnapshot;
		this.createdAt = Instant.now();
		this.status = JobStatus.QUEUED;
	}

	public synchronized void markRunning() {
		if (status != JobStatus.QUEUED) {
			return;
		}
		status = JobStatus.RUNNING;
		startedAt = Instant.now();
	}

	public synchronized void markSucceeded(ExecutionResult result) {
		markSucceeded(result, null);
	}

	public synchronized void markSucceeded(ExecutionResult result, String executionRecordId) {
		this.result = result;
		this.executionRecordId = executionRecordId;
		this.resultSummary = summarize(result);
		status = JobStatus.SUCCESS;
		completedAt = Instant.now();
	}

	public synchronized void markFailed(ExecutionResult result, String error) {
		markFailed(result, error, null);
	}

	public synchronized void markFailed(ExecutionResult result, String error, String executionRecordId) {
		this.result = result;
		this.executionRecordId = executionRecordId;
		this.resultSummary = summarize(result);
		this.errorMessage = error;
		status = JobStatus.FAILED;
		completedAt = Instant.now();
	}

	private String summarize(ExecutionResult executionResult) {
		if (executionResult == null) {
			return null;
		}
		if (executionResult.getMessage() != null && !executionResult.getMessage().isBlank()) {
			return executionResult.getMessage();
		}
		return executionResult.getOutput();
	}

	public String getId() {
		return id;
	}

	public String getTaskId() {
		return taskId;
	}

	public TaskDefinition getTaskSnapshot() {
		return taskSnapshot;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public JobStatus getStatus() {
		return status;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public ExecutionResult getResult() {
		return result;
	}

	public String getExecutionRecordId() {
		return executionRecordId;
	}

	public String getResultSummary() {
		return resultSummary;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public String getError() {
		return errorMessage;
	}
}
