package com.aidevos.orchestrator.pr.provider;

/**
 * Remote pull request provider abstraction backed by a git host (GitHub,
 * GitLab or a local mock). Implementations talk to the host REST API and map
 * request/response into {@link GitPullRequestResult}. This phase only creates
 * and manages pull request state; it never merges, pulls or modifies code.
 */
public interface GitProvider {

	GitPullRequestResult createPullRequest(GitPullRequestRequest request);

	GitPullRequestResult getPullRequest(String externalId);

	GitPullRequestResult closePullRequest(String externalId);

	GitPullRequestResult mergePullRequest(String externalId);
}
