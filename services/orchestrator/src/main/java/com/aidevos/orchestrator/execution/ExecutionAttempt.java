package com.aidevos.orchestrator.execution;

import java.time.Instant;

import com.aidevos.orchestrator.job.JobLease;

public class ExecutionAttempt {

	private final String id;
	private final String jobId;
	private final int attemptNo;
	private final Instant createdAt;
	private volatile String executionId;
	private volatile ExecutionAttemptStatus status = ExecutionAttemptStatus.STARTING;
	private volatile String leaseOwner;
	private volatile Long leaseToken;
	private volatile Instant leaseExpiresAt;
	private volatile Instant heartbeatAt;
	private volatile String failureCode;
	private volatile int recoveryCount;
	private volatile Instant startedAt;
	private volatile Instant completedAt;

	public ExecutionAttempt(String id, String jobId, int attemptNo) {
		this(id, jobId, attemptNo, Instant.now());
	}

	public static ExecutionAttempt restore(String id, String jobId, int attemptNo,
			String executionId, ExecutionAttemptStatus status, String leaseOwner, Long leaseToken,
			Instant leaseExpiresAt, Instant heartbeatAt, String failureCode, int recoveryCount,
			Instant createdAt, Instant startedAt, Instant completedAt) {
		ExecutionAttempt attempt = new ExecutionAttempt(id, jobId, attemptNo, createdAt);
		attempt.executionId = executionId;
		attempt.status = status == null ? ExecutionAttemptStatus.STARTING : status;
		attempt.leaseOwner = leaseOwner;
		attempt.leaseToken = leaseToken;
		attempt.leaseExpiresAt = leaseExpiresAt;
		attempt.heartbeatAt = heartbeatAt;
		attempt.failureCode = failureCode;
		attempt.recoveryCount = recoveryCount;
		attempt.startedAt = startedAt;
		attempt.completedAt = completedAt;
		return attempt;
	}

	private ExecutionAttempt(String id, String jobId, int attemptNo, Instant createdAt) {
		this.id = id;
		this.jobId = jobId;
		this.attemptNo = attemptNo;
		this.createdAt = createdAt;
	}

	public synchronized boolean markRunning(Instant time) {
		if (status != ExecutionAttemptStatus.STARTING) {
			return false;
		}
		status = ExecutionAttemptStatus.RUNNING;
		startedAt = time;
		return true;
	}

	public synchronized boolean markSucceeded(Instant time) {
		if (status != ExecutionAttemptStatus.RUNNING) {
			return false;
		}
		status = ExecutionAttemptStatus.SUCCEEDED;
		completedAt = time;
		return true;
	}

	public synchronized boolean markFailed(String failureCode, Instant time) {
		if (status != ExecutionAttemptStatus.RUNNING) {
			return false;
		}
		this.failureCode = failureCode;
		status = ExecutionAttemptStatus.FAILED;
		completedAt = time;
		return true;
	}

	public synchronized boolean markAbandoned(Instant time) {
		if (status != ExecutionAttemptStatus.RUNNING) {
			return false;
		}
		status = ExecutionAttemptStatus.ABANDONED;
		completedAt = time;
		return true;
	}

	public synchronized boolean markRecoveryRequired(String failureCode) {
		if (status != ExecutionAttemptStatus.RUNNING
				&& status != ExecutionAttemptStatus.ABANDONED) {
			return false;
		}
		this.failureCode = failureCode;
		status = ExecutionAttemptStatus.RECOVERY_REQUIRED;
		return true;
	}

	public synchronized void applyLease(JobLease lease) {
		this.leaseOwner = lease.owner();
		this.leaseToken = lease.token();
		this.leaseExpiresAt = lease.expiresAt();
	}

	public synchronized void touchHeartbeat(Instant time) {
		this.heartbeatAt = time;
	}

	public synchronized void clearLease() {
		this.leaseOwner = null;
		this.leaseToken = null;
		this.leaseExpiresAt = null;
		this.heartbeatAt = null;
	}

	public synchronized int incrementRecoveryCount() {
		return ++recoveryCount;
	}

	public synchronized void setExecutionId(String executionId) {
		this.executionId = executionId;
	}

	public String getId() {
		return id;
	}

	public String getJobId() {
		return jobId;
	}

	public int getAttemptNo() {
		return attemptNo;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getExecutionId() {
		return executionId;
	}

	public synchronized ExecutionAttemptStatus getStatus() {
		return status;
	}

	public String getLeaseOwner() {
		return leaseOwner;
	}

	public Long getLeaseToken() {
		return leaseToken;
	}

	public Instant getLeaseExpiresAt() {
		return leaseExpiresAt;
	}

	public Instant getHeartbeatAt() {
		return heartbeatAt;
	}

	public String getFailureCode() {
		return failureCode;
	}

	public int getRecoveryCount() {
		return recoveryCount;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}
}
