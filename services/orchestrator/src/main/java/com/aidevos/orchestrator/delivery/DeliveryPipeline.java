package com.aidevos.orchestrator.delivery;

import java.time.Instant;

/**
 * Persisted aggregate for one task's delivery pipeline. It records which
 * stage is current, which underlying entities were already produced
 * (validation run, quality gate, commit, approval, push, PR, CI run) so that
 * advance() can recover after a restart and never re-executes completed work.
 * All state transitions are explicit; restore() is only used by repositories.
 */
public final class DeliveryPipeline {

	private final String taskId;
	private volatile String changeSetId = "";
	private volatile String executionWorkspaceId = "";
	private volatile DeliveryStage currentStage = DeliveryStage.CHANGE_READY;
	private volatile DeliveryStatus status = DeliveryStatus.RUNNING;
	private volatile String validationRunId = "";
	private volatile String qualityGateId = "";
	private volatile String commitId = "";
	private volatile String remotePushApprovalId = "";
	private volatile String remoteBranchId = "";
	private volatile String pullRequestId = "";
	private volatile String ciRunId = "";
	private volatile DeliveryFailureClass failureClass;
	private volatile String failureReason = "";
	private volatile Instant createdAt;
	private volatile Instant updatedAt;
	private volatile Instant completedAt;

	public DeliveryPipeline(String taskId, Instant createdAt) {
		this.taskId = taskId;
		this.createdAt = createdAt == null ? Instant.now() : createdAt;
		this.updatedAt = this.createdAt;
	}

	private DeliveryPipeline(String taskId, String changeSetId, String executionWorkspaceId,
			DeliveryStage currentStage, DeliveryStatus status, String validationRunId,
			String qualityGateId, String commitId, String remotePushApprovalId,
			String remoteBranchId, String pullRequestId, String ciRunId,
			DeliveryFailureClass failureClass, String failureReason, Instant createdAt,
			Instant updatedAt, Instant completedAt) {
		this.taskId = taskId;
		this.changeSetId = changeSetId == null ? "" : changeSetId;
		this.executionWorkspaceId = executionWorkspaceId == null ? "" : executionWorkspaceId;
		this.currentStage = currentStage == null ? DeliveryStage.CHANGE_READY : currentStage;
		this.status = status == null ? DeliveryStatus.RUNNING : status;
		this.validationRunId = validationRunId == null ? "" : validationRunId;
		this.qualityGateId = qualityGateId == null ? "" : qualityGateId;
		this.commitId = commitId == null ? "" : commitId;
		this.remotePushApprovalId = remotePushApprovalId == null ? "" : remotePushApprovalId;
		this.remoteBranchId = remoteBranchId == null ? "" : remoteBranchId;
		this.pullRequestId = pullRequestId == null ? "" : pullRequestId;
		this.ciRunId = ciRunId == null ? "" : ciRunId;
		this.failureClass = failureClass;
		this.failureReason = failureReason == null ? "" : failureReason;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt == null ? createdAt : updatedAt;
		this.completedAt = completedAt;
	}

	/** Reconstructs a persisted pipeline without running state transitions. */
	public static DeliveryPipeline restore(String taskId, String changeSetId,
			String executionWorkspaceId, DeliveryStage currentStage, DeliveryStatus status,
			String validationRunId, String qualityGateId, String commitId,
			String remotePushApprovalId, String remoteBranchId, String pullRequestId,
			String ciRunId, DeliveryFailureClass failureClass, String failureReason,
			Instant createdAt, Instant updatedAt, Instant completedAt) {
		return new DeliveryPipeline(taskId, changeSetId, executionWorkspaceId, currentStage,
			status, validationRunId, qualityGateId, commitId, remotePushApprovalId,
			remoteBranchId, pullRequestId, ciRunId, failureClass, failureReason, createdAt,
			updatedAt, completedAt);
	}

	public synchronized void bindChangeSet(String changeSetId) {
		this.changeSetId = changeSetId;
		this.updatedAt = Instant.now();
	}

	public synchronized void bindExecutionWorkspace(String executionWorkspaceId) {
		this.executionWorkspaceId = executionWorkspaceId;
		this.updatedAt = Instant.now();
	}

	public synchronized void bindValidation(String validationRunId) {
		this.validationRunId = validationRunId;
		this.updatedAt = Instant.now();
	}

	public synchronized void bindQualityGate(String qualityGateId) {
		this.qualityGateId = qualityGateId;
		this.updatedAt = Instant.now();
	}

	public synchronized void bindCommit(String commitId) {
		this.commitId = commitId;
		this.updatedAt = Instant.now();
	}

	public synchronized void bindApproval(String remotePushApprovalId) {
		this.remotePushApprovalId = remotePushApprovalId;
		this.updatedAt = Instant.now();
	}

	public synchronized void bindPush(String remoteBranchId) {
		this.remoteBranchId = remoteBranchId;
		this.updatedAt = Instant.now();
	}

	public synchronized void bindPullRequest(String pullRequestId) {
		this.pullRequestId = pullRequestId;
		this.updatedAt = Instant.now();
	}

	public synchronized void bindCiRun(String ciRunId) {
		this.ciRunId = ciRunId;
		this.updatedAt = Instant.now();
	}

	/** Moves to a deterministic stage; never touches a terminal pipeline. */
	public synchronized void advanceTo(DeliveryStage stage) {
		requireNotTerminal();
		this.currentStage = stage;
		this.status = DeliveryStatus.RUNNING;
		this.updatedAt = Instant.now();
	}

	/** Stops at a human gate (quality gate approval, remote push approval). */
	public synchronized void markWaitingApproval() {
		requireNotTerminal();
		this.status = DeliveryStatus.WAITING_APPROVAL;
		this.updatedAt = Instant.now();
	}

	public synchronized void markComplete() {
		requireNotTerminal();
		this.currentStage = DeliveryStage.DELIVERY_COMPLETE;
		this.status = DeliveryStatus.COMPLETE;
		this.completedAt = Instant.now();
		this.updatedAt = this.completedAt;
	}

	/**
	 * Re-opens a FAILED pipeline for a manual retry. The historical bindings
	 * and counters are kept; reconcile() derives the next stage from the
	 * underlying entities so already completed work is not re-executed.
	 */
	/**
	 * Re-opens a pipeline stopped at a human gate after the human decision
	 * (approve). Keeps all bindings; the next advance() reconciles the new
	 * decision and continues.
	 */
	public synchronized void resumeFromWaitingApproval() {
		if (this.status != DeliveryStatus.WAITING_APPROVAL) {
			throw new IllegalStateException("Only a WAITING_APPROVAL delivery pipeline can resume");
		}
		this.status = DeliveryStatus.RUNNING;
		this.updatedAt = Instant.now();
	}

	public synchronized void resumeFromFailure() {
		if (this.status != DeliveryStatus.FAILED) {
			throw new IllegalStateException("Only a FAILED delivery pipeline can resume");
		}
		this.status = DeliveryStatus.RUNNING;
		this.updatedAt = Instant.now();
	}

	public synchronized void markFailed(DeliveryFailureClass failureClass, String reason) {
		if (this.status == DeliveryStatus.COMPLETE) {
			throw new IllegalStateException("A completed delivery pipeline cannot fail");
		}
		this.currentStage = DeliveryStage.FAILED;
		this.status = DeliveryStatus.FAILED;
		this.failureClass = failureClass;
		this.failureReason = reason == null ? "" : reason;
		this.updatedAt = Instant.now();
	}

	private void requireNotTerminal() {
		if (this.status == DeliveryStatus.COMPLETE || this.status == DeliveryStatus.FAILED) {
			throw new IllegalStateException("Delivery pipeline is terminal: " + this.status);
		}
	}

	public String getTaskId() {
		return taskId;
	}

	public String getChangeSetId() {
		return changeSetId;
	}

	public String getExecutionWorkspaceId() {
		return executionWorkspaceId;
	}

	public DeliveryStage getCurrentStage() {
		return currentStage;
	}

	public DeliveryStatus getStatus() {
		return status;
	}

	public String getValidationRunId() {
		return validationRunId;
	}

	public String getQualityGateId() {
		return qualityGateId;
	}

	public String getCommitId() {
		return commitId;
	}

	public String getRemotePushApprovalId() {
		return remotePushApprovalId;
	}

	public String getRemoteBranchId() {
		return remoteBranchId;
	}

	public String getPullRequestId() {
		return pullRequestId;
	}

	public String getCiRunId() {
		return ciRunId;
	}

	public DeliveryFailureClass getFailureClass() {
		return failureClass;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}
}
