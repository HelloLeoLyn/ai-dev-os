package com.aidevos.orchestrator.agent;

import java.util.List;

import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.springframework.stereotype.Component;

@Component
public class AgentResolver {

	private final AgentManager agentManager;
	private final AgentSelector agentSelector;
	private final ExecutorManager executorManager;

	public AgentResolver(AgentManager agentManager, AgentSelector agentSelector,
			ExecutorManager executorManager) {
		this.agentManager = agentManager;
		this.agentSelector = agentSelector;
		this.executorManager = executorManager;
	}

	public ResolvedAgent resolve(TaskDefinition taskDefinition) {
		AgentDefinition agent = resolveDefinition(taskDefinition);
		validateEnabled(agent);
		validateCapabilities(agent, taskDefinition.getRequiredCapabilities());
		AgentExecutor executor = executorManager.getExecutor(agent.getName());
		if (executor == null) {
			throw new AgentResolutionException("Executor not found: " + agent.getExecutor()
				+ " for agent: " + agent.getName());
		}
		return new ResolvedAgent(agent, executor);
	}

	private AgentDefinition resolveDefinition(TaskDefinition taskDefinition) {
		String agentName = taskDefinition.getAgentName();
		if (agentName != null && !agentName.isBlank()) {
			AgentDefinition agent = agentManager.getAgent(agentName);
			if (agent == null) {
				throw new AgentResolutionException("Agent not found: " + agentName);
			}
			return agent;
		}

		List<String> capabilities = taskDefinition.getRequiredCapabilities();
		AgentDefinition selected = agentSelector.select(capabilities);
		if (selected == null) {
			throw new AgentResolutionException("Agent not found for required capabilities: " + capabilities);
		}
		return selected;
	}

	private void validateEnabled(AgentDefinition agent) {
		if (!agent.isEnabled()) {
			throw new AgentResolutionException("Agent is disabled: " + agent.getName());
		}
	}

	private void validateCapabilities(AgentDefinition agent, List<String> requiredCapabilities) {
		if (requiredCapabilities == null || requiredCapabilities.isEmpty()) {
			return;
		}
		List<String> capabilities = agent.getCapabilities();
		if (capabilities == null || !capabilities.containsAll(requiredCapabilities)) {
			throw new AgentResolutionException("Agent " + agent.getName()
				+ " does not provide required capabilities: " + requiredCapabilities);
		}
	}
}
