package com.aidevos.orchestrator.pr.provider;

/**
 * Input for opening a pull request: the internal pullRequestId is kept for
 * correlation and logging, the branch fields describe the source and target
 * branches of the change.
 */
public record GitPullRequestRequest(String pullRequestId, String branch, String targetBranch,
		String title, String description) {
}
