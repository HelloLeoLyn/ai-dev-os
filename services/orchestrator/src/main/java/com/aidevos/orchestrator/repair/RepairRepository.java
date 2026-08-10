package com.aidevos.orchestrator.repair;

import java.util.List;

/**
 * Persistence contract for repair tasks. The repair coordinator keeps its
 * runtime state; this repository is the durable projection used by the
 * PostgreSQL persistence layer and by the repository tests.
 */
public interface RepairRepository {

	void save(RepairTask task);

	RepairTask get(String repairId);

	List<RepairTask> getByTaskId(String taskId);

	List<RepairTask> list();
}
