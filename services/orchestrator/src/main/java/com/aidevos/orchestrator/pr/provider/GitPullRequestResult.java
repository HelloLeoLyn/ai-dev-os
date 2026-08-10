package com.aidevos.orchestrator.pr.provider;

/**
 * Result of a remote pull request operation: the provider-side external id
 * (GitHub pull number / GitLab merge request iid), the web url and the
 * provider-reported state.
 */
public record GitPullRequestResult(String externalId, String url, String state) {
}
