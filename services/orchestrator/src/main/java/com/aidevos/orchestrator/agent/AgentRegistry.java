package com.aidevos.orchestrator.agent;

import java.util.List;
import java.util.Optional;

/**
 * Registry of known agents: registration, lookup by id, listing and
 * capability-based discovery used by the AgentSelector.
 */
public interface AgentRegistry {

	void register(AgentDefinition agent);

	Optional<AgentDefinition> getAgent(String agentId);

	List<AgentDefinition> listAgents();

	List<AgentDefinition> findByCapability(String taskCategory);
}
