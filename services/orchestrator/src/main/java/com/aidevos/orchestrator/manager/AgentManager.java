package com.aidevos.orchestrator.manager;

import com.aidevos.orchestrator.model.AgentDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AgentManager {

	private final Map<String, AgentDefinition> agents = new LinkedHashMap<>();

	public void register(AgentDefinition agentDefinition) {
		agents.put(agentDefinition.getName(), agentDefinition);
	}

	public AgentDefinition getAgent(String name) {
		return agents.get(name);
	}

	public List<AgentDefinition> getAllAgents() {
		return new ArrayList<>(agents.values());
	}

	public AgentDefinition removeAgent(String name) {
		return agents.remove(name);
	}
}
