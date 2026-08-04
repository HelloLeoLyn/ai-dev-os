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
	private volatile String approvalId;
	private volatile int attemptNo;
	private volatile int maxAttempts = 1;
	private volatile Instant availableAt;
	private volatile int priority;
	private volatile String leaseOwner;
	private volatile Long leaseToken;
	private volatile Instant leaseExpiresAt;
	private volatile Instant heartbeatAt;
	private volatile int version;
	private volatile int recoveryCount;
	private volatile String lastFailureCode;
	private volatile RecoveryPolicy recoveryPolicy = RecoveryPolicy.MANUAL;

	public ExecutionJob(String id, TaskDefinition taskSnapshot) {
		this.id = id;
		this.taskId = taskSnapshot.getId();
		this.taskSnapshot = taskSnapshot;
		this.createdAt = Instant.now();
		this.status = JobStatus.QUEUED;
	}

	public static ExecutionJob restore(String id, TaskDefinition taskSnapshot, Instant createdAt,
			JobStatus status, Instant startedAt, Instant completedAt, ExecutionResult result,
			String executionRecordId, String resultSummary, String errorMessage, String approvalId) {
		return new ExecutionJob(id, taskSnapshot, createdAt, status, startedAt, completedAt,
			result, executionRecordId, resultSummary, errorMessage, approvalId);
	}

	/**
	 * Restores a job including the control fields persisted for lease, attempt
	 * and recovery bookkeeping.
	 */
	public static ExecutionJob restore(String id, TaskDefinition taskSnapshot, Instant createdAt,
			JobStatus status, Instant startedAt, Instant completedAt, ExecutionResult result,
			String executionRecordId, String resultSummary, String errorMessage, String approvalId,
			int attemptNo, int maxAttempts, Instant availableAt, int priority, String leaseOwner,
			Long leaseToken, Instant leaseExpiresAt, Instant heartbeatAt, int version,
			int recoveryCount, String lastFailureCode, RecoveryPolicy recoveryPolicy) {
		ExecutionJob job = restore(id, taskSnapshot, createdAt, status, startedAt, completedAt,
			result, executionRecordId, resultSummary, errorMessage, approvalId);
		job.attemptNo = attemptNo;
		job.maxAttempts = maxAttempts;
		job.availableAt = availableAt;
		job.priority = priority;
		job.leaseOwner = leaseOwner;
		job.leaseToken = leaseToken;
		job.leaseExpiresAt = leaseExpiresAt;
		job.heartbeatAt = heartbeatAt;
		job.version = version;
		job.recoveryCount = recoveryCount;
		job.lastFailureCode = lastFailureCode;
		job.recoveryPolicy = recoveryPolicy == null ? RecoveryPolicy.MANUAL : recoveryPolicy;
		return job;
	}

	private ExecutionJob(String id, TaskDefinition taskSnapshot, Instant createdAt, JobStatus status,
			Instant startedAt, Instant completedAt, ExecutionResult result, String executionRecordId,
			String resultSummary, String errorMessage, String approvalId) {
		this.id=id; this.taskId=taskSnapshot.getId(); this.taskSnapshot=taskSnapshot;
		this.createdAt=createdAt; this.status=status; this.startedAt=startedAt;
		this.completedAt=completedAt; this.result=result; this.executionRecordId=executionRecordId;
		this.resultSummary=resultSummary; this.errorMessage=errorMessage; this.approvalId=approvalId;
	}

	public synchronized void markRunning() {
		if (status != JobStatus.QUEUED && status != JobStatus.RETRY_WAIT) {
			return;
		}
		status = JobStatus.RUNNING;
		startedAt = Instant.now();
	}

	/**
	 * Applies a lease granted by a worker. Does not change the job status.
	 */
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
		this.leaseExpiresAt = null;
		this.heartbeatAt = null;
	}

	/**
	 * Moves a running job back to the queue after its lease has been released.
	 * The fencing token is retained by clearLease so the next claim continues
	 * the monotonic sequence.
	 */
	public synchronized boolean requeue() {
		if (status != JobStatus.RUNNING) {
			return false;
		}
		status = JobStatus.QUEUED;
		return true;
	}

	public synchronized int nextAttemptNo() {
		return ++attemptNo;
	}

	public synchronized int bumpVersion() {
		return ++version;
	}

	public synchronized int incrementRecoveryCount() {
		return ++recoveryCount;
	}

	public synchronized boolean markRetryWait(String failureCode, Instant availableAt) {
		if (status != JobStatus.RUNNING) {
			return false;
		}
		this.lastFailureCode = failureCode;
		this.availableAt = availableAt;
		status = JobStatus.RETRY_WAIT;
		return true;
	}

	public synchronized boolean markRecoveryRequired(String failureCode) {
		if (status != JobStatus.RUNNING) {
			return false;
		}
		this.lastFailureCode = failureCode;
		status = JobStatus.RECOVERY_REQUIRED;
		return true;
	}

	public synchronized boolean markCancelled() {
		if (status == JobStatus.SUCCESS || status == JobStatus.FAILED) {
			return false;
		}
		status = JobStatus.CANCELLED;
		completedAt = Instant.now();
		return true;
	}

	public synchronized void setMaxAttempts(int maxAttempts) {
		this.maxAttempts = maxAttempts;
	}

	public synchronized void setPriority(int priority) {
		this.priority = priority;
	}

	public synchronized void setRecoveryPolicy(RecoveryPolicy recoveryPolicy) {
		this.recoveryPolicy = recoveryPolicy == null ? RecoveryPolicy.MANUAL : recoveryPolicy;
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

	public synchronized void markWaitingApproval(ExecutionResult result, String executionRecordId) {
		this.result = result;
		this.executionRecordId = executionRecordId;
		this.approvalId = result.getApprovalId();
		status = JobStatus.WAITING_APPROVAL;
	}

	public synchronized boolean resumeAfterApproval() {
		if (status != JobStatus.WAITING_APPROVAL) {
			return false;
		}
		status = JobStatus.QUEUED;
		return true;
	}

	public synchronized void restoreWaitingApproval() {
		if (status == JobStatus.QUEUED && approvalId != null) {
			status = JobStatus.WAITING_APPROVAL;
		}
	}

	public synchronized boolean rejectApproval() {
		if (status != JobStatus.WAITING_APPROVAL) {
			return false;
		}
		status = JobStatus.FAILED;
		errorMessage = "Approval rejected";
		completedAt = Instant.now();
		return true;
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

	public String getApprovalId() { return approvalId; }

	public int getAttemptNo() { return attemptNo; }
	public int getMaxAttempts() { return maxAttempts; }
	public Instant getAvailableAt() { return availableAt; }
	public int getPriority() { return priority; }
	public String getLeaseOwner() { return leaseOwner; }
	public Long getLeaseToken() { return leaseToken; }
	public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
	public Instant getHeartbeatAt() { return heartbeatAt; }
	public int getVersion() { return version; }
	public int getRecoveryCount() { return recoveryCount; }
	public String getLastFailureCode() { return lastFailureCode; }
	public RecoveryPolicy getRecoveryPolicy() { return recoveryPolicy; }

	public enum RecoveryPolicy {
		REQUEUE,
		RECONCILE,
		MANUAL
	}
}
