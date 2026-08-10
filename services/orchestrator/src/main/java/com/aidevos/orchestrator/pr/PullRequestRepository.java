package com.aidevos.orchestrator.pr;

import java.util.List;

/**
 * Store for pull request records, keyed by pull request id and queryable by
 * task.
 */
public interface PullRequestRepository {

	void save(PullRequestRecord record);

	PullRequestRecord get(String pullRequestId);

	List<PullRequestRecord> getByTaskId(String taskId);

	List<PullRequestRecord> list();
}
