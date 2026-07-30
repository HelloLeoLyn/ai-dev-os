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

		assertEquals(3, agents.size());
		assertAgent(agents.get(0), "planner", "mock", List.of("analysis"));
		assertAgent(agents.get(1), "executor", "mock", List.of("coding", "git"));
		assertAgent(agents.get(2), "coder", "codex", List.of("coding", "git"));
		assertEquals("Executes coding tasks", agents.get(2).getDescription());
		assertEquals("system", agents.get(2).getType());
		assertEquals("standard", agents.get(2).getPermissionLevel());
		assertEquals(true, agents.get(2).isEnabled());
	}

	private void assertAgent(AgentDefinition agent, String name, String executor, List<String> capabilities) {
		assertEquals(name, agent.getName());
		assertEquals(executor, agent.getExecutor());
		assertEquals(capabilities, agent.getCapabilities());
	}
}
