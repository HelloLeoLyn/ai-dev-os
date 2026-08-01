package com.aidevos.orchestrator.execution;

import java.util.ArrayList;
import java.util.List;

public class ExecutionResult {

	private boolean success;
	private String message;
	private String output;
	private List<ExecutionArtifact> artifacts = new ArrayList<>();

	public ExecutionResult() {
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
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

	public List<ExecutionArtifact> getArtifacts() {
		return artifacts;
	}

	public void setArtifacts(List<ExecutionArtifact> artifacts) {
		this.artifacts = artifacts;
	}
}
