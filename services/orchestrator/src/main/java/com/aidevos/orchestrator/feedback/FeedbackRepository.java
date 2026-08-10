package com.aidevos.orchestrator.feedback;

import java.util.List;

/**
 * Store for pull request feedback records, keyed by feedback id and queryable
 * by task, pull request and CI run.
 */
public interface FeedbackRepository {

	void save(PrFeedbackRecord record);

	PrFeedbackRecord get(String feedbackId);

	List<PrFeedbackRecord> getByTaskId(String taskId);

	List<PrFeedbackRecord> getByPullRequestId(String pullRequestId);

	List<PrFeedbackRecord> getByCiRunId(String ciRunId);

	List<PrFeedbackRecord> list();
}
