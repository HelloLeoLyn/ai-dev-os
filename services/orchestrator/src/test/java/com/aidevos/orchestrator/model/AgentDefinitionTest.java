package com.aidevos.orchestrator.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDefinitionTest {

	@Test
	void shouldStoreAndReturnProperties() {
		AgentDefinition agentDefinition = new AgentDefinition();

		agentDefinition.setName("planner");
		agentDefinition.setType("system");
		agentDefinition.setDescription("Plans agent tasks");
		agentDefinition.setPermissionLevel("standard");
		agentDefinition.setEnabled(true);

		assertEquals("planner", agentDefinition.getName());
		assertEquals("system", agentDefinition.getType());
		assertEquals("Plans agent tasks", agentDefinition.getDescription());
		assertEquals("standard", agentDefinition.getPermissionLevel());
		assertTrue(agentDefinition.isEnabled());
	}
}
