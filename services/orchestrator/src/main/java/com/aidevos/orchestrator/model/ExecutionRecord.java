package com.aidevos.orchestrator.model;

import com.aidevos.orchestrator.execution.ExecutionReport;
import com.aidevos.orchestrator.execution.ExecutionArtifact;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

public class ExecutionRecord {

	private String id;
	private String taskId;
	private String agentName;
	private String executorName;
	private String operation;
	private String status;
	private String message;
	private String output;
	private ExecutionReport report;
	private List<ExecutionArtifact> artifacts = new ArrayList<>();
	private String executionId;
	private String jobId;
	private String planRunId;
	private String stepRunId;
	private String attemptId;
	private String workspace;
	private String sandbox;
	private String approvalId;
	private String branch;
	private String beforeHead;
	private String afterHead;
	private Integer exitCode;
	private String codexThreadId;
	private String gitStatus;
	private String gitDiffStat;
	private String requestedModelId;
	private String resolvedModelId;
	private String modelProvider;
	private String modelExecutor;
	private String errorCode;
	private String errorMessage;
	private Instant startedAt;
	private Instant completedAt;

	public ExecutionRecord() {
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public String getAgentName() {
		return agentName;
	}

	public void setAgentName(String agentName) {
		this.agentName = agentName;
	}
	public String getExecutorName() { return executorName; }
	public void setExecutorName(String executorName) { this.executorName = executorName; }
	public String getOperation() { return operation; }
	public void setOperation(String operation) { this.operation = operation; }

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getOutput() {
		return output;
	}

	public void setOutput(String output) {
		this.output = output;
	}

	public ExecutionReport getReport() {
		return report;
	}

	public void setReport(ExecutionReport report) {
		this.report = report;
	}

	public List<ExecutionArtifact> getArtifacts() {
		return artifacts;
	}

	public void setArtifacts(List<ExecutionArtifact> artifacts) {
		this.artifacts = artifacts;
	}

	public String getExecutionId() { return executionId; }
	public void setExecutionId(String executionId) { this.executionId = executionId; }
	public String getJobId() { return jobId; }
	public void setJobId(String jobId) { this.jobId = jobId; }
	public String getPlanRunId() { return planRunId; }
	public void setPlanRunId(String planRunId) { this.planRunId = planRunId; }
	public String getStepRunId() { return stepRunId; }
	public void setStepRunId(String stepRunId) { this.stepRunId = stepRunId; }
	public String getAttemptId() { return attemptId; }
	public void setAttemptId(String attemptId) { this.attemptId = attemptId; }
	public String getWorkspace() { return workspace; }
	public void setWorkspace(String workspace) { this.workspace = workspace; }
	public String getSandbox() { return sandbox; }
	public void setSandbox(String sandbox) { this.sandbox = sandbox; }
	public String getApprovalId() { return approvalId; }
	public void setApprovalId(String approvalId) { this.approvalId = approvalId; }
	public String getBranch() { return branch; }
	public void setBranch(String branch) { this.branch = branch; }
	public String getBeforeHead() { return beforeHead; }
	public void setBeforeHead(String beforeHead) { this.beforeHead = beforeHead; }
	public String getAfterHead() { return afterHead; }
	public void setAfterHead(String afterHead) { this.afterHead = afterHead; }
	public String getRequestedModelId() { return requestedModelId; }
	public void setRequestedModelId(String requestedModelId) { this.requestedModelId = requestedModelId; }
	public String getResolvedModelId() { return resolvedModelId; }
	public void setResolvedModelId(String resolvedModelId) { this.resolvedModelId = resolvedModelId; }
	public String getModelProvider() { return modelProvider; }
	public void setModelProvider(String modelProvider) { this.modelProvider = modelProvider; }
	public String getModelExecutor() { return modelExecutor; }
	public void setModelExecutor(String modelExecutor) { this.modelExecutor = modelExecutor; }
	public String getErrorCode() { return errorCode; }
	public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
	public String getErrorMessage() { return errorMessage; }
	public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
	public Integer getExitCode() { return exitCode; }
	public void setExitCode(Integer exitCode) { this.exitCode = exitCode; }
	public String getCodexThreadId() { return codexThreadId; }
	public void setCodexThreadId(String codexThreadId) { this.codexThreadId = codexThreadId; }
	public String getGitStatus() { return gitStatus; }
	public void setGitStatus(String gitStatus) { this.gitStatus = gitStatus; }
	public String getGitDiffStat() { return gitDiffStat; }
	public void setGitDiffStat(String gitDiffStat) { this.gitDiffStat = gitDiffStat; }
	public Instant getStartedAt() { return startedAt; }
	public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
	public Instant getCompletedAt() { return completedAt; }
	public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
