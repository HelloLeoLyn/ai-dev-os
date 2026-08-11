package com.aidevos.orchestrator.collaboration;

import java.util.List;

/**
 * Persistence contract for agent team messages. Implemented by the
 * in-memory store; no database migration is introduced in this phase.
 */
public interface AgentMessageRepository {

	void save(AgentMessage message);

	List<AgentMessage> listByTeam(String teamId);

	List<AgentMessage> list();
}
