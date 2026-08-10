package com.aidevos.orchestrator.pr;

import com.aidevos.orchestrator.pr.provider.GitProvider;
import com.aidevos.orchestrator.pr.provider.GitPullRequestRequest;
import com.aidevos.orchestrator.pr.provider.GitPullRequestResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default pull request provider (aidevos.git.provider=mock) used when no real
 * remote host is wired in. It never talks to a real API: create returns a
 * deterministic mock URL and close/merge are no-ops that return the same URL.
 */
@Component
@ConditionalOnProperty(prefix = "aidevos.git", name = "provider", havingValue = "mock",
	matchIfMissing = true)
public class MockPullRequestProvider implements GitProvider {

	private static final String BASE_URL = "https://mock.dev/pr/";

	@Override
	public GitPullRequestResult createPullRequest(GitPullRequestRequest request) {
		return result(request.pullRequestId(), "open");
	}

	@Override
	public GitPullRequestResult getPullRequest(String externalId) {
		return result(externalId, "open");
	}

	@Override
	public GitPullRequestResult closePullRequest(String externalId) {
		return result(externalId, "closed");
	}

	@Override
	public GitPullRequestResult mergePullRequest(String externalId) {
		return result(externalId, "merged");
	}

	private GitPullRequestResult result(String externalId, String state) {
		return new GitPullRequestResult(externalId, BASE_URL + externalId, state);
	}
}
