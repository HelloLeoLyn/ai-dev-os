package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import org.springframework.stereotype.Component;

@Component
public class ExecutorManager {

	private final AgentManager agentManager;
	private final MockAgentExecutor mockAgentExecutor;

	public ExecutorManager(AgentManager agentManager, MockAgentExecutor mockAgentExecutor) {
		this.agentManager = agentManager;
		this.mockAgentExecutor = mockAgentExecutor;
	}

	public AgentExecutor getExecutor(String agentName) {
		AgentDefinition agentDefinition = agentManager.getAgent(agentName);
		if (agentDefinition == null) {
			return null;
		}
		if ("mock".equals(agentDefinition.getExecutor())) {
			return mockAgentExecutor;
		}
		return null;
	}
}
