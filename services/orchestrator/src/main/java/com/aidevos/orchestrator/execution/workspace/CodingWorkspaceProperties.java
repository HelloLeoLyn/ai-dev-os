package com.aidevos.orchestrator.execution.workspace;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "coding.workspace")
public class CodingWorkspaceProperties {

	private List<String> allowedRoots = new ArrayList<>();
	private String executionWorkspaceRoot = System.getProperty("java.io.tmpdir") + "/ai-dev-os-worktrees";

	public List<String> getAllowedRoots() {
		return allowedRoots;
	}

	public void setAllowedRoots(List<String> allowedRoots) {
		this.allowedRoots = allowedRoots;
	}
	public String getExecutionWorkspaceRoot() { return executionWorkspaceRoot; }
	public void setExecutionWorkspaceRoot(String value) { if (value != null && !value.isBlank()) executionWorkspaceRoot = value; }
}
