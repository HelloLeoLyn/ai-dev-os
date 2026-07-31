package com.aidevos.orchestrator.model;

import com.aidevos.orchestrator.execution.ExecutionReport;

public class ExecutionRecord {

	private String id;
	private String taskId;
	private String agentName;
	private String status;
	private String message;
	private String output;
	private ExecutionReport report;

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
}
