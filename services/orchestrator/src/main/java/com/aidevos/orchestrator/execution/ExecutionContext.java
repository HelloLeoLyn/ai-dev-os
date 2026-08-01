package com.aidevos.orchestrator.execution;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExecutionContext {

	private String executionId;
	private String jobId;
	private String taskId;
	private String taskName;
	private String description;
	private String agentName;
	private String externalAgentId;
	private String input;
	private String workspace;
	private Map<String, Object> metadata = new LinkedHashMap<>();
	private Map<String, Object> parameters = new LinkedHashMap<>();

	public ExecutionContext() {
	}

	public String getExecutionId() {
		return executionId;
	}

	public void setExecutionId(String executionId) {
		this.executionId = executionId;
	}

	public String getJobId() {
		return jobId;
	}

	public void setJobId(String jobId) {
		this.jobId = jobId;
	}

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public String getTaskName() {
		return taskName;
	}

	public void setTaskName(String taskName) {
		this.taskName = taskName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getAgentName() {
		return agentName;
	}

	public void setAgentName(String agentName) {
		this.agentName = agentName;
	}

	public String getExternalAgentId() {
		return externalAgentId;
	}

	public void setExternalAgentId(String externalAgentId) {
		this.externalAgentId = externalAgentId;
	}

	public String getInput() {
		return input;
	}

	public void setInput(String input) {
		this.input = input;
	}

	public String getWorkspace() {
		return workspace;
	}

	public void setWorkspace(String workspace) {
		this.workspace = workspace;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}

	public Map<String, Object> getParameters() {
		return parameters;
	}

	public void setParameters(Map<String, Object> parameters) {
		this.parameters = parameters;
	}
}
