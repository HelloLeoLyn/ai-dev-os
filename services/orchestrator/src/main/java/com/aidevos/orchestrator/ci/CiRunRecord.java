package com.aidevos.orchestrator.ci;

import java.time.Instant;

/**
 * One CI run observed for a pull request: the provider pipeline id, the
 * reported status, the commit under test and the report url. This phase only
 * records and updates status; it never triggers repairs or modifies code.
 */
public class CiRunRecord {

	private final String ciRunId;
	private final String taskId;
	private final String pullRequestId;
	private final String provider;
	private final String branch;
	private final String commitHash;
	private final Instant startedAt;
	private volatile String pipelineId = "";
	private volatile CiStatus status = CiStatus.PENDING;
	private volatile String reportUrl = "";
	private volatile Instant finishedAt;

	public CiRunRecord(String ciRunId, String taskId, String pullRequestId, String provider,
			String branch, String commitHash, Instant startedAt) {
		this.ciRunId = ciRunId;
		this.taskId = taskId;
		this.pullRequestId = pullRequestId;
		this.provider = provider == null ? "" : provider;
		this.branch = branch == null ? "" : branch;
		this.commitHash = commitHash == null ? "" : commitHash;
		this.startedAt = startedAt;
	}

	public synchronized void updatePipelineId(String pipelineId) {
		this.pipelineId = pipelineId == null ? "" : pipelineId;
	}

	public synchronized void updateReportUrl(String reportUrl) {
		this.reportUrl = reportUrl == null ? "" : reportUrl;
	}

	public synchronized void markRunning() {
		requireStatus(CiStatus.PENDING, "Only a PENDING run can start");
		this.status = CiStatus.RUNNING;
	}

	public synchronized void markSuccess() {
		requireStatus(CiStatus.RUNNING, "Only a RUNNING run can succeed");
		this.status = CiStatus.SUCCESS;
		this.finishedAt = Instant.now();
	}

	public synchronized void markFailed() {
		requireStatus(CiStatus.RUNNING, "Only a RUNNING run can fail");
		this.status = CiStatus.FAILED;
		this.finishedAt = Instant.now();
	}

	public synchronized void markCancelled() {
		requireStatus(CiStatus.RUNNING, "Only a RUNNING run can be cancelled");
		this.status = CiStatus.CANCELLED;
		this.finishedAt = Instant.now();
	}

	private void requireStatus(CiStatus expected, String message) {
		if (this.status != expected) {
			throw new IllegalStateException(message + " (current: " + this.status + ")");
		}
	}

	private CiRunRecord(String ciRunId, String taskId, String pullRequestId, String provider,
			String pipelineId, CiStatus status, String branch, String commitHash,
			String reportUrl, Instant startedAt, Instant finishedAt) {
		this.ciRunId = ciRunId;
		this.taskId = taskId;
		this.pullRequestId = pullRequestId;
		this.provider = provider == null ? "" : provider;
		this.pipelineId = pipelineId == null ? "" : pipelineId;
		this.status = status == null ? CiStatus.PENDING : status;
		this.branch = branch == null ? "" : branch;
		this.commitHash = commitHash == null ? "" : commitHash;
		this.reportUrl = reportUrl == null ? "" : reportUrl;
		this.startedAt = startedAt;
		this.finishedAt = finishedAt;
	}

	/**
	 * Reconstructs a persisted CI run without running state transitions. Used
	 * by the PostgreSQL repository.
	 */
	public static CiRunRecord restore(String ciRunId, String taskId, String pullRequestId,
			String provider, String pipelineId, CiStatus status, String branch,
			String commitHash, String reportUrl, Instant startedAt, Instant finishedAt) {
		return new CiRunRecord(ciRunId, taskId, pullRequestId, provider, pipelineId, status,
			branch, commitHash, reportUrl, startedAt, finishedAt);
	}

	public String getCiRunId() {
		return ciRunId;
	}

	public String getTaskId() {
		return taskId;
	}

	public String getPullRequestId() {
		return pullRequestId;
	}

	public String getProvider() {
		return provider;
	}

	public String getPipelineId() {
		return pipelineId;
	}

	public CiStatus getStatus() {
		return status;
	}

	public String getBranch() {
		return branch;
	}

	public String getCommitHash() {
		return commitHash;
	}

	public String getReportUrl() {
		return reportUrl;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}
}
