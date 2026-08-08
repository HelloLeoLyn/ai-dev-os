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

	/**
	 * Returns the full working-tree diff (patch) of the git repository at the
	 * given path, or an empty string when no diff is present or the directory
	 * is not a git repository.
	 */
	String patch(String path);

	/**
	 * Stages all working-tree changes, creates a commit with the given message
	 * and returns the new HEAD hash, or an empty string when the commit fails
	 * (for example nothing to commit). Never pushes, merges or touches remotes.
	 */
	String commit(String path, String message);

	/**
	 * Returns the current HEAD hash of the repository, or an empty string when
	 * the directory is not a git repository.
	 */
	String currentCommitHash(String path);
}
