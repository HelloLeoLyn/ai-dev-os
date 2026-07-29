package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.manager.AgentManager;
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
		if (agentManager.getAgent(agentName) == null) {
			return null;
		}
		return mockAgentExecutor;
	}
}
