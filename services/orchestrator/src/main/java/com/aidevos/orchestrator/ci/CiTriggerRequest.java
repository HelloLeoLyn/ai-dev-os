package com.aidevos.orchestrator.ci;

/**
 * Input for associating a CI run with a pull request: the pull request id,
 * the branch under test and the commit hash the CI runs against.
 */
public record CiTriggerRequest(String pullRequestId, String branch, String commitHash) {
}
