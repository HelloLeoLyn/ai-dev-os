package com.aidevos.orchestrator.ci;

import java.util.List;

/**
 * Store for CI run records, keyed by ci run id and queryable by task and pull
 * request.
 */
public interface CiRepository {

	void save(CiRunRecord record);

	CiRunRecord get(String ciRunId);

	List<CiRunRecord> getByTaskId(String taskId);

	List<CiRunRecord> getByPullRequestId(String pullRequestId);

	List<CiRunRecord> list();
}
