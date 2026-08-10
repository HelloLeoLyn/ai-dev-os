package com.aidevos.orchestrator.pr;

/**
 * Lifecycle of one pull request managed by the orchestrator: CREATED -> OPEN
 * -> MERGED | CLOSED, or FAILED when the provider rejects the request. This
 * phase only manages state; no real merge is performed.
 */
public enum PullRequestStatus {
	CREATED,
	OPEN,
	MERGED,
	CLOSED,
	FAILED
}
