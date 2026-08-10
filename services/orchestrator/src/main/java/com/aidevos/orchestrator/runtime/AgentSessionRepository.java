package com.aidevos.orchestrator.runtime;

import java.util.List;

/**
 * Persistence contract for runtime sessions and their checkpoints.
 * Implemented by the in-memory store; no database migration is introduced
 * in this phase.
 */
public interface AgentSessionRepository {

	void save(AgentSession session);

	AgentSession get(String sessionId);

	List<AgentSession> listByTask(String taskId);

	List<AgentSession> list();

	void saveCheckpoint(AgentCheckpoint checkpoint);

	List<AgentCheckpoint> listCheckpoints(String sessionId);
}
