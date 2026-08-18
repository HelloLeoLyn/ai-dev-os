package com.aidevos.orchestrator.executor.codex;

/** Server-verified scope for one already-approved execution invocation. */
public record ApprovedExecutionHandoff(
		String taskId,
		String planId,
		int planVersion,
		String jobId,
		String executionWorkspace,
		String approvalId,
		String authority,
		String operation) {
}
