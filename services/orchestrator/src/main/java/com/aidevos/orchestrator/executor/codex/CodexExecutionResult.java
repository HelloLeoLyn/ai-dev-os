package com.aidevos.orchestrator.executor.codex;

/**
 * Structured outcome of a codex CLI execution: exit code, stdout/stderr, the
 * workspace it ran in and the git state observed afterwards. Git status and
 * diff stat are captured as text here; the persisted ExecutionRecord is
 * enriched separately through the WorkspaceService.
 */
public record CodexExecutionResult(
		boolean success,
		int exitCode,
		String stdout,
		String stderr,
		String workspace,
		String branch,
		String gitStatus,
		String gitDiffStat) {

	public static CodexExecutionResult of(boolean success, int exitCode, String stdout,
			String stderr, String workspace, String branch, String gitStatus,
			String gitDiffStat) {
		return new CodexExecutionResult(success, exitCode, stdout, stderr, workspace, branch,
			gitStatus, gitDiffStat);
	}
}
