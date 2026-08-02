package com.aidevos.orchestrator.plan;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.executor.ExecutorRegistry;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.tool.ToolRegistry;
import org.springframework.stereotype.Component;

@Component
public class PlanSnapshotFactory {

	private final AgentManager agentManager;
	private final ToolRegistry toolRegistry;
	private final ExecutorRegistry executorRegistry;

	public PlanSnapshotFactory(AgentManager agentManager, ToolRegistry toolRegistry,
			ExecutorRegistry executorRegistry) {
		this.agentManager = agentManager;
		this.toolRegistry = toolRegistry;
		this.executorRegistry = executorRegistry;
	}

	public PlanSnapshot capture(String policyVersion, Map<String, Object> plannerMetadata) {
		var agents = agentManager.getAllAgents().stream()
			.map(agent -> new PlanSnapshot.AgentSnapshot(agent.getName(), agent.getExecutor(),
				agent.getCapabilities(), agent.getPermissionLevel(), agent.isEnabled()))
			.toList();
		Set<String> capabilities = new LinkedHashSet<>();
		agents.forEach(agent -> capabilities.addAll(agent.capabilities()));
		var tools = toolRegistry.getTools().stream()
			.map(tool -> new PlanSnapshot.ToolSnapshot(tool.providerId(), tool.name(), tool.access()))
			.toList();
		return new PlanSnapshot(agents, capabilities, tools, executorRegistry.getTypes(),
			policyVersion, plannerMetadata);
	}
}
