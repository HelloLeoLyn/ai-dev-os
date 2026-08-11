package com.aidevos.orchestrator.goal;

import java.util.List;

/**
 * Persistence contract for autonomous goals. Implemented by the in-memory
 * store; no database migration is introduced in this phase.
 */
public interface GoalRepository {

	void save(Goal goal);

	Goal get(String goalId);

	List<Goal> list();

	List<Goal> listByProject(String projectId);
}
