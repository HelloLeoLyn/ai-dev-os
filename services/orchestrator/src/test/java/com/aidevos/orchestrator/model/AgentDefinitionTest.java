package com.aidevos.orchestrator.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDefinitionTest {

	@Test
	void shouldStoreAndReturnProperties() {
		AgentDefinition agentDefinition = new AgentDefinition();

		agentDefinition.setName("planner");
		agentDefinition.setExecutor("mock");
		agentDefinition.setExecutorConfig(Map.of("endpoint", "local"));
		agentDefinition.setCapabilities(List.of("analysis"));
		agentDefinition.setType("system");
		agentDefinition.setDescription("Plans agent tasks");
		agentDefinition.setPermissionLevel("standard");
		agentDefinition.setEnabled(true);

		assertEquals("planner", agentDefinition.getName());
		assertEquals("mock", agentDefinition.getExecutor());
		assertEquals(Map.of("endpoint", "local"), agentDefinition.getExecutorConfig());
		assertEquals(List.of("analysis"), agentDefinition.getCapabilities());
		assertEquals("system", agentDefinition.getType());
		assertEquals("Plans agent tasks", agentDefinition.getDescription());
		assertEquals("standard", agentDefinition.getPermissionLevel());
		assertTrue(agentDefinition.isEnabled());
	}
}
