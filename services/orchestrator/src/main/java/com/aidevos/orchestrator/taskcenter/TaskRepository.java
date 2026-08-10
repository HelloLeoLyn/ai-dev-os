package com.aidevos.orchestrator.taskcenter;

import java.util.List;

/**
 * Persistence contract for Task Center tasks. The task center service keeps
 * its runtime map; this repository is the durable projection used by the
 * PostgreSQL persistence layer and by the repository tests.
 */
public interface TaskRepository {

	void save(TaskRecord task);

	TaskRecord get(String taskId);

	List<TaskRecord> list();

	List<TaskRecord> listByProject(String projectId);
}
