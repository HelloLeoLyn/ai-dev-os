package com.aidevos.orchestrator.optimization;

import java.util.List;

/**
 * Persistence contract for optimization records. Implemented by the
 * in-memory store; no database migration is introduced in this phase.
 */
public interface OptimizationRepository {

	void save(OptimizationRecord record);

	OptimizationRecord get(String id);

	List<OptimizationRecord> listByTask(String taskId);

	List<OptimizationRecord> list();
}
