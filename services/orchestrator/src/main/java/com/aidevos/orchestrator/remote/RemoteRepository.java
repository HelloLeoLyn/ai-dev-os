package com.aidevos.orchestrator.remote;

import java.util.List;

/**
 * Store for remote branch push records, keyed by remote id and queryable by
 * task.
 */
public interface RemoteRepository {

	void save(RemoteBranchRecord record);

	RemoteBranchRecord get(String remoteId);

	List<RemoteBranchRecord> getByTaskId(String taskId);

	List<RemoteBranchRecord> list();
}
