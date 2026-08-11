package com.aidevos.orchestrator.collaboration;

import java.util.List;

/**
 * Persistence contract for agent teams. Implemented by the in-memory store;
 * no database migration is introduced in this phase.
 */
public interface AgentTeamRepository {

	void save(AgentTeam team);

	AgentTeam get(String teamId);

	List<AgentTeam> listByTask(String taskId);

	List<AgentTeam> list();
}
