package com.aidevos.orchestrator.change;

import java.util.List;

/**
 * Store for AI change sets, keyed by change id and queryable by task.
 */
public interface ChangeRepository {

	void save(ChangeSet changeSet);

	ChangeSet get(String changeId);

	List<ChangeSet> getByTaskId(String taskId);

	List<ChangeSet> list();
}
