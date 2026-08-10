package com.aidevos.orchestrator.agent;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRegistryTest {

	private final InMemoryAgentRegistry registry = new InMemoryAgentRegistry();

	@Test
	void shouldRegisterAndLookupAgent() {
		AgentDefinition agent = new AgentDefinition("custom-1", AgentType.CODEX,
			new AgentCapability(AgentType.CODEX, "custom-1", "custom coder",
				List.of("CODE_TASK"), List.of("codex-cli")), "ACTIVE", 99);
		registry.register(agent);

		Optional<AgentDefinition> found = registry.getAgent("custom-1");
		assertTrue(found.isPresent());
		assertSame(agent, found.orElseThrow());
	}

	@Test
	void shouldListAgentsOrderedByPriority() {
		List<AgentDefinition> agents = registry.listAgents();
		assertEquals(5, agents.size());
		assertEquals("hermes", agents.get(0).getAgentId());
		assertEquals("repair-agent", agents.get(4).getAgentId());
		assertTrue(agents.stream().allMatch(agent -> "ACTIVE".equals(agent.getStatus())));
	}

	@Test
	void shouldFindByCapability() {
		List<AgentDefinition> coders = registry.findByCapability("CODE_GENERATION");
		assertFalse(coders.isEmpty());
		assertEquals(AgentType.CODEX, coders.get(0).getAgentType());

		List<AgentDefinition> testers = registry.findByCapability("TEST_VERIFY");
		assertFalse(testers.isEmpty());
		assertEquals(AgentType.TEST_AGENT, testers.get(0).getAgentType());
	}

	@Test
	void shouldReturnEmptyForUnknownCapability() {
		assertTrue(registry.findByCapability("UNKNOWN_CAPABILITY").isEmpty());
		assertTrue(registry.getAgent("missing").isEmpty());
	}

	@Test
	void shouldRejectBlankAgentId() {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
			() -> registry.register(new AgentDefinition(" ", AgentType.HERMES,
				new AgentCapability(AgentType.HERMES, "x", "x", List.of(), List.of()),
				"ACTIVE", 1)));
	}

	@Test
	void shouldExposeCapabilityDetails() {
		AgentDefinition codex = registry.getAgent("codex").orElseThrow();
		assertNotNull(codex.getCapabilities());
		assertEquals("codex", codex.getCapabilities().name());
		assertTrue(codex.getCapabilities().supports("CODE_TASK"));
		assertTrue(codex.getCapabilities().supports("CODING"));
	}
}
