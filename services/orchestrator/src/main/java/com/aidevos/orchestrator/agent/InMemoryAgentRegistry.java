package com.aidevos.orchestrator.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

/**
 * In-memory agent registry pre-populated with the standard AI Dev OS agent
 * catalog (hermes, codex, openclaw, test-agent, repair-agent). Additional
 * agents can be registered at runtime.
 */
@Repository
public class InMemoryAgentRegistry implements AgentRegistry {

	private final Map<String, AgentDefinition> agents = new LinkedHashMap<>();

	public InMemoryAgentRegistry() {
		register(defaultAgent("hermes", AgentType.HERMES,
			"需求理解与执行计划生成", List.of("TASK_ANALYSIS", "PLANNING", "HERMES"),
			List.of("planner"), 10));
		register(defaultAgent("codex", AgentType.CODEX,
			"在 Workspace 中修改代码并生成 ChangeSet",
			List.of("CODE_TASK", "CODE_GENERATION", "CODING"), List.of("codex-cli", "workspace",
				"git"), 20));
		register(defaultAgent("openclaw", AgentType.OPENCLAW,
			"驱动浏览器执行端到端任务",
			List.of("BROWSER_TASK", "BROWSER_TEST", "BROWSER"), List.of("browser"), 30));
		register(defaultAgent("test-agent", AgentType.TEST_AGENT,
			"执行测试并生成 TestReport",
			List.of("TEST_TASK", "TEST_VERIFY", "TESTING"), List.of("test-runner"), 40));
		register(defaultAgent("repair-agent", AgentType.REPAIR_AGENT,
			"分析失败并生成修复计划",
			List.of("REPAIR_TASK"), List.of("planner", "codex-cli", "test-runner"), 50));
	}

	@Override
	public synchronized void register(AgentDefinition agent) {
		if (agent == null || agent.getAgentId() == null || agent.getAgentId().isBlank()) {
			throw new IllegalArgumentException("agentId is required");
		}
		agents.put(agent.getAgentId(), agent);
	}

	@Override
	public synchronized Optional<AgentDefinition> getAgent(String agentId) {
		if (agentId == null || agentId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(agents.get(agentId));
	}

	@Override
	public synchronized List<AgentDefinition> listAgents() {
		List<AgentDefinition> result = new ArrayList<>(agents.values());
		result.sort(Comparator.comparingInt(AgentDefinition::getPriority));
		return result;
	}

	@Override
	public synchronized List<AgentDefinition> findByCapability(String taskCategory) {
		List<AgentDefinition> result = new ArrayList<>();
		for (AgentDefinition agent : agents.values()) {
			if (agent.getCapabilities() != null
				&& agent.getCapabilities().supports(taskCategory)) {
				result.add(agent);
			}
		}
		result.sort(Comparator.comparingInt(AgentDefinition::getPriority));
		return result;
	}

	private AgentDefinition defaultAgent(String id, AgentType type, String description,
			List<String> tasks, List<String> tools, int priority) {
		return new AgentDefinition(id, type,
			new AgentCapability(type, id, description, tasks, tools), "ACTIVE", priority);
	}
}
