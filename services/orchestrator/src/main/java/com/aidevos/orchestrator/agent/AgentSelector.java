package com.aidevos.orchestrator.agent;

import java.util.List;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.mcp.tool.McpToolRouter;
import com.aidevos.orchestrator.mcp.tool.ToolDefinition;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.modelrouter.TaskType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Agent selection. The legacy capability-based selection (AgentManager +
 * List of required capabilities) is kept for the existing execution flow;
 * the orchestration upgrade adds task-category -> AgentType mapping and
 * registry capability matching (CODE_TASK -> CODEX, BROWSER_TASK ->
 * OPENCLAW, TEST_TASK -> TEST_AGENT, REPAIR_TASK -> REPAIR_AGENT).
 */
@Component
public class AgentSelector {

	private final AgentManager agentManager;
	private final AgentRegistry registry;
	private final McpToolRouter toolRouter;

	public AgentSelector(AgentManager agentManager) {
		this(agentManager, null, null);
	}

	public AgentSelector(AgentManager agentManager, AgentRegistry registry) {
		this(agentManager, registry, null);
	}

	@Autowired
	public AgentSelector(AgentManager agentManager, AgentRegistry registry,
			McpToolRouter toolRouter) {
		this.agentManager = agentManager;
		this.registry = registry;
		this.toolRouter = toolRouter;
	}

	/**
	 * Binds the tool capability of an agent type (CODEX -> git + filesystem,
	 * OPENCLAW -> browser, TEST_AGENT -> terminal, REPAIR_AGENT -> git +
	 * filesystem). Returns an empty list when no tool router is configured.
	 */
	public List<ToolDefinition> selectTools(AgentType agentType) {
		return toolRouter == null ? List.of() : toolRouter.toolsFor(agentType);
	}

	/**
	 * Legacy selection: returns the registered agent whose capabilities
	 * contain all required capabilities, or null when no agent matches.
	 */
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

	public AgentType selectType(TaskType taskType) {
		return selectType(taskType == null ? "GENERAL" : taskType.name());
	}

	public AgentType selectType(String taskCategory) {
		if (taskCategory == null || taskCategory.isBlank()) {
			return AgentType.HERMES;
		}
		return switch (taskCategory.trim().toUpperCase()) {
			case "CODE_TASK", "CODE_GENERATION", "CODING" -> AgentType.CODEX;
			case "BROWSER_TASK", "BROWSER_TEST", "BROWSER" -> AgentType.OPENCLAW;
			case "TEST_TASK", "TEST_VERIFY", "TESTING" -> AgentType.TEST_AGENT;
			case "REPAIR_TASK", "REPAIR" -> AgentType.REPAIR_AGENT;
			default -> AgentType.HERMES;
		};
	}

	/**
	 * Orchestration selection: picks the registered agent for a task category
	 * through the agent registry capability matching, falling back to the
	 * agent whose type matches the mapped AgentType.
	 */
	public com.aidevos.orchestrator.agent.AgentDefinition selectAgent(String taskCategory) {
		if (registry == null) {
			return null;
		}
		List<com.aidevos.orchestrator.agent.AgentDefinition> matches =
			registry.findByCapability(taskCategory);
		if (!matches.isEmpty()) {
			return matches.get(0);
		}
		AgentType type = selectType(taskCategory);
		return registry.listAgents().stream()
			.filter(agent -> agent.getAgentType() == type)
			.findFirst()
			.orElse(null);
	}

	public com.aidevos.orchestrator.agent.AgentDefinition selectAgent(TaskType taskType) {
		return selectAgent(taskType == null ? "GENERAL" : taskType.name());
	}
}
