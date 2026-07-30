package com.aidevos.orchestrator.agent;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentSelector {

	private final AgentManager agentManager;

	public AgentSelector(AgentManager agentManager) {
		this.agentManager = agentManager;
	}

	public AgentDefinition select(List<String> requiredCapabilities) {
		if (requiredCapabilities == null || requiredCapabilities.isEmpty()) {
			return null;
		}

		for (AgentDefinition agentDefinition : agentManager.getAllAgents()) {
			List<String> capabilities = agentDefinition.getCapabilities();
			if (capabilities != null && capabilities.containsAll(requiredCapabilities)) {
				return agentDefinition;
			}
		}
		return null;
	}
}
