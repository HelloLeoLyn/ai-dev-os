package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import org.springframework.stereotype.Component;

@Component
public class ExecutorManager {

	private final AgentManager agentManager;
	private final ExecutorRegistry executorRegistry;

	public ExecutorManager(AgentManager agentManager, ExecutorRegistry executorRegistry) {
		this.agentManager = agentManager;
		this.executorRegistry = executorRegistry;
	}

	public AgentExecutor getExecutor(String agentName) {
		AgentDefinition agentDefinition = agentManager.getAgent(agentName);
		if (agentDefinition == null) {
			return null;
		}
		return executorRegistry.get(agentDefinition.getExecutor());
	}
}
