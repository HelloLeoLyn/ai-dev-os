package com.aidevos.orchestrator.execution.workspace;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "coding.workspace")
public class CodingWorkspaceProperties {

	private List<String> allowedRoots = new ArrayList<>();

	public List<String> getAllowedRoots() {
		return allowedRoots;
	}

	public void setAllowedRoots(List<String> allowedRoots) {
		this.allowedRoots = allowedRoots;
	}
}
