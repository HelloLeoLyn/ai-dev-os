package com.aidevos.orchestrator.workspace.git;

/**
 * Single entry point for all git invocations from the workspace layer. Callers
 * must never scatter raw ProcessBuilder/Runtime.exec calls; every git command
 * goes through an implementation of this interface.
 */
public interface GitCommandExecutor {

	/**
	 * Returns the current branch and porcelain status counts of the git
	 * repository at the given path.
	 */
	GitStatus status(String path);

	/**
	 * Returns the diff stat summary of the git repository at the given path.
	 */
	GitDiff diff(String path);
}
