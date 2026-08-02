package com.aidevos.orchestrator.approval;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "coding.approval")
public class CodingApprovalProperties {

	private boolean requiredForWorkspaceWrite = true;

	public boolean isRequiredForWorkspaceWrite() {
		return requiredForWorkspaceWrite;
	}

	public void setRequiredForWorkspaceWrite(boolean requiredForWorkspaceWrite) {
		this.requiredForWorkspaceWrite = requiredForWorkspaceWrite;
	}
}
