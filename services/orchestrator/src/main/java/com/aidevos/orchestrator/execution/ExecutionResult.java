package com.aidevos.orchestrator.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class ExecutionResult {

	private boolean success;
	private String message;
	private String output;
	private List<ExecutionArtifact> artifacts = new ArrayList<>();
	private boolean approvalRequired;
	private String approvalId;
	private Map<String, Object> metadata = new LinkedHashMap<>();

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

	public boolean isApprovalRequired() { return approvalRequired; }
	public void setApprovalRequired(boolean approvalRequired) { this.approvalRequired = approvalRequired; }
	public String getApprovalId() { return approvalId; }
	public void setApprovalId(String approvalId) { this.approvalId = approvalId; }
	public Map<String, Object> getMetadata() { return metadata; }
	public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
