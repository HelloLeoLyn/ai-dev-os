package com.aidevos.orchestrator.pr;

/**
 * Optional overrides for a pull request creation action. When a field is
 * absent the service derives its default (target branch main, title and
 * description generated from the task).
 */
public record PullRequestCreateRequest(String targetBranch, String title, String description) {
}
