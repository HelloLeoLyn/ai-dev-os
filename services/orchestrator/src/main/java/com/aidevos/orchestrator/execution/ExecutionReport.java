package com.aidevos.orchestrator.execution;

public class ExecutionReport {

	private String taskId;
	private String agentName;
	private boolean success;
	private String beforeGitStatus;
	private String afterGitDiff;
	private String output;

	public ExecutionReport() {
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

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public String getBeforeGitStatus() {
		return beforeGitStatus;
	}

	public void setBeforeGitStatus(String beforeGitStatus) {
		this.beforeGitStatus = beforeGitStatus;
	}

	public String getAfterGitDiff() {
		return afterGitDiff;
	}

	public void setAfterGitDiff(String afterGitDiff) {
		this.afterGitDiff = afterGitDiff;
	}

	public String getOutput() {
		return output;
	}

	public void setOutput(String output) {
		this.output = output;
	}
}
