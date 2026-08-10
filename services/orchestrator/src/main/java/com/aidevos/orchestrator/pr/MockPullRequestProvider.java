package com.aidevos.orchestrator.pr;

import org.springframework.stereotype.Component;

/**
 * Test/mock pull request provider used until a real remote host is wired in.
 * It never talks to a real API: create returns a deterministic mock URL and
 * close/merge are no-ops that return the same URL.
 */
@Component
public class MockPullRequestProvider implements PullRequestProvider {

	private static final String BASE_URL = "https://mock.dev/pr/";

	@Override
	public String create(String pullRequestId, String branch, String targetBranch,
			String title, String description) {
		return url(pullRequestId);
	}

	@Override
	public String get(String pullRequestId) {
		return url(pullRequestId);
	}

	@Override
	public String close(String pullRequestId) {
		return url(pullRequestId);
	}

	@Override
	public String merge(String pullRequestId) {
		return url(pullRequestId);
	}

	private String url(String pullRequestId) {
		return BASE_URL + pullRequestId;
	}
}
