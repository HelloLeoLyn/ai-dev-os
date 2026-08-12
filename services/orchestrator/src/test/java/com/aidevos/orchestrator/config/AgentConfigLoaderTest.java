package com.aidevos.orchestrator.config;

import com.aidevos.orchestrator.model.AgentDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentConfigLoaderTest {

	@Test
	void shouldLoadAgentsFromYaml() {
		AgentConfigLoader agentConfigLoader = new AgentConfigLoader();

		List<AgentDefinition> agents = agentConfigLoader.loadAgents();

		assertEquals(7, agents.size());
		assertAgent(agents.get(0), "planner", "mock", List.of("planning", "analysis"));
		assertAgent(agents.get(1), "analyst", "codex", List.of("analysis", "read-only"));
		assertAgent(agents.get(2), "executor", "mock", List.of("coding", "git"));
		assertAgent(agents.get(3), "coder", "codex", List.of("coding", "git"));
		assertAgent(agents.get(4), "tester", "openclaw", List.of("testing", "browser"));
		assertAgent(agents.get(5), "browser-agent", "openclaw", List.of("browser"));
		assertAgent(agents.get(6), "mcp-reader", "tool", List.of("tool", "read-only"));
		assertEquals("main", agents.get(4).getExecutorConfig().get("agentId"));
		assertEquals("main", agents.get(5).getExecutorConfig().get("agentId"));
		assertEquals(null, agents.get(3).getExecutorConfig().get("workspace"));
		assertEquals(null, agents.get(3).getExecutorConfig().get("model"));
		assertEquals("1.0.0", agents.get(3).getVersion());
		assertEquals("1.0.0", agents.get(0).getVersion());
		assertEquals("Executes coding tasks", agents.get(3).getDescription());
		assertEquals("system", agents.get(3).getType());
		assertEquals("standard", agents.get(3).getPermissionLevel());
		assertEquals(true, agents.get(3).isEnabled());
		assertEquals("read-only", agents.get(1).getPermissionLevel());
		assertEquals("read-only", agents.get(6).getPermissionLevel());
	}

	private void assertAgent(AgentDefinition agent, String name, String executor, List<String> capabilities) {
		assertEquals(name, agent.getName());
		assertEquals(executor, agent.getExecutor());
		assertEquals(capabilities, agent.getCapabilities());
	}
}
