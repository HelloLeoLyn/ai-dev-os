package com.aidevos.orchestrator.config;

import com.aidevos.orchestrator.model.AgentDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentConfigLoaderTest {

	@Test
	void shouldLoadAgentsFromYaml() {
		AgentConfigLoader agentConfigLoader = new AgentConfigLoader();

		List<AgentDefinition> agents = agentConfigLoader.loadAgents();

		assertEquals(2, agents.size());
		assertAgent(agents.get(0), "planner", "system", "Plans agent tasks", "standard", true);
		assertAgent(agents.get(1), "executor", "system", "Executes agent tasks", "standard", false);
	}

	private void assertAgent(AgentDefinition agent, String name, String type, String description,
			String permissionLevel, boolean enabled) {
		assertEquals(name, agent.getName());
		assertEquals(type, agent.getType());
		assertEquals(description, agent.getDescription());
		assertEquals(permissionLevel, agent.getPermissionLevel());
		if (enabled) {
			assertTrue(agent.isEnabled());
		}
		else {
			assertFalse(agent.isEnabled());
		}
	}
}
