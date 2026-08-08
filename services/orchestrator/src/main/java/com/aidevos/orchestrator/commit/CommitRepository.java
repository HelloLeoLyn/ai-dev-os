package com.aidevos.orchestrator.commit;

import java.util.List;

/**
 * Store for commit records, keyed by commit id and queryable by task.
 */
public interface CommitRepository {

	void save(CommitRecord record);

	CommitRecord get(String commitId);

	List<CommitRecord> getByTaskId(String taskId);

	List<CommitRecord> list();
}
