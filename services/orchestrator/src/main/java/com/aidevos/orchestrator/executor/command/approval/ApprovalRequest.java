package com.aidevos.orchestrator.executor.command.approval;

import java.util.List;

public record ApprovalRequest(List<String> command, String workingDirectory, String ruleId) {

	public ApprovalRequest {
		command = List.copyOf(command);
	}
}
