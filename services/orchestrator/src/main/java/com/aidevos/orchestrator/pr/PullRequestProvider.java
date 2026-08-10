package com.aidevos.orchestrator.pr;

/**
 * Provider abstraction for a pull request host. This phase ships
 * MockPullRequestProvider only; a real GitHub/GitLab provider can implement
 * this interface later without touching the service.
 */
public interface PullRequestProvider {

	String create(String pullRequestId, String branch, String targetBranch, String title,
			String description);

	String get(String pullRequestId);

	String close(String pullRequestId);

	String merge(String pullRequestId);
}
