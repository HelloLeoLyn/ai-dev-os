package com.aidevos.orchestrator.orchestrator;

import java.util.List;

/**
 * Persistence contract for the orchestrator task queue. Implemented by the
 * in-memory store; no database migration is introduced in this phase.
 */
public interface TaskQueueRepository {

	void add(OrchestrationTask task);

	boolean remove(String taskId);

	/** The next task to schedule: highest priority first, FIFO within it. */
	OrchestrationTask next();

	OrchestrationTask get(String taskId);

	List<OrchestrationTask> list();
}
