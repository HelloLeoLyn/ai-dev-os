package com.aidevos.orchestrator.repair;

import java.time.Instant;

/**
 * One repair run for a failed task: its state machine, bounded retry counter,
 * the failure context that triggered it and the last attempt result.
 */
public class RepairTask {

	private final String repairId;
	private final String taskId;
	private final String workspaceId;
	private final FailureContext failureContext;
	private final Instant createdAt;
	private volatile RepairStatus status = RepairStatus.PENDING;
	private volatile int retryCount = 0;
	private volatile String lastResult;
	private volatile Instant updatedAt;

	public RepairTask(String repairId, String taskId, String workspaceId,
			FailureContext failureContext) {
		this.repairId = repairId;
		this.taskId = taskId;
		this.workspaceId = workspaceId;
		this.failureContext = failureContext;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	public synchronized void markAnalyzing() {
		this.status = RepairStatus.ANALYZING;
		this.updatedAt = Instant.now();
	}

	public synchronized void markFixing() {
		this.status = RepairStatus.FIXING;
		this.updatedAt = Instant.now();
	}

	public synchronized void markVerifying() {
		this.status = RepairStatus.VERIFYING;
		this.updatedAt = Instant.now();
	}

	public synchronized void markSuccess(String result) {
		this.status = RepairStatus.SUCCESS;
		this.lastResult = result;
		this.updatedAt = Instant.now();
	}

	public synchronized void markFailed(String result) {
		this.status = RepairStatus.FAILED;
		this.lastResult = result;
		this.updatedAt = Instant.now();
	}

	public synchronized void incrementRetry() {
		this.retryCount++;
		this.updatedAt = Instant.now();
	}

	private RepairTask(String repairId, String taskId, String workspaceId,
			FailureContext failureContext, RepairStatus status, int retryCount,
			String lastResult, Instant createdAt, Instant updatedAt) {
		this.repairId = repairId;
		this.taskId = taskId;
		this.workspaceId = workspaceId;
		this.failureContext = failureContext;
		this.createdAt = createdAt == null ? Instant.now() : createdAt;
		this.status = status == null ? RepairStatus.PENDING : status;
		this.retryCount = retryCount;
		this.lastResult = lastResult;
		this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
	}

	/**
	 * Reconstructs a persisted repair task without running state transitions.
	 * Used by the PostgreSQL repository.
	 */
	public static RepairTask restore(String repairId, String taskId, String workspaceId,
			FailureContext failureContext, RepairStatus status, int retryCount,
			String lastResult, Instant createdAt, Instant updatedAt) {
		return new RepairTask(repairId, taskId, workspaceId, failureContext, status,
			retryCount, lastResult, createdAt, updatedAt);
	}

	public String getRepairId() {
		return repairId;
	}

	public String getTaskId() {
		return taskId;
	}

	public String getWorkspaceId() {
		return workspaceId;
	}

	public FailureContext getFailureContext() {
		return failureContext;
	}

	public RepairStatus getStatus() {
		return status;
	}

	public int getRetryCount() {
		return retryCount;
	}

	public String getLastResult() {
		return lastResult;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
