package com.aidevos.orchestrator.manager;

import com.aidevos.orchestrator.model.AgentDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AgentManagerTest {

	@Test
	void shouldRegisterAndGetAgent() {
		AgentManager agentManager = new AgentManager();
		AgentDefinition agentDefinition = createAgent("planner");

		agentManager.register(agentDefinition);

		assertSame(agentDefinition, agentManager.getAgent("planner"));
	}

	@Test
	void shouldGetAllAgents() {
		AgentManager agentManager = new AgentManager();
		AgentDefinition planner = createAgent("planner");
		AgentDefinition executor = createAgent("executor");

		agentManager.register(planner);
		agentManager.register(executor);

		assertEquals(List.of(planner, executor), agentManager.getAllAgents());
	}

	@Test
	void shouldRemoveAgent() {
		AgentManager agentManager = new AgentManager();
		AgentDefinition agentDefinition = createAgent("planner");
		agentManager.register(agentDefinition);

		AgentDefinition removedAgent = agentManager.removeAgent("planner");

		assertSame(agentDefinition, removedAgent);
		assertNull(agentManager.getAgent("planner"));
		assertEquals(List.of(), agentManager.getAllAgents());
	}

	private AgentDefinition createAgent(String name) {
		AgentDefinition agentDefinition = new AgentDefinition();
		agentDefinition.setName(name);
		return agentDefinition;
	}
}
