package com.aidevos.orchestrator.manager;

import com.aidevos.orchestrator.model.AgentDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class AgentManager {

	private final AgentRepository repository;

	public AgentManager() { this(new InMemoryAgentRepository()); }

	@Autowired
	public AgentManager(AgentRepository repository) { this.repository = repository; }

	public void register(AgentDefinition agentDefinition) {
		repository.save(agentDefinition);
	}

	public AgentDefinition getAgent(String name) {
		return repository.get(name);
	}

	public List<AgentDefinition> getAllAgents() {
		return repository.getAll();
	}

	public AgentDefinition removeAgent(String name) {
		AgentDefinition existing = repository.get(name);
		repository.remove(name);
		return existing;
	}
}
