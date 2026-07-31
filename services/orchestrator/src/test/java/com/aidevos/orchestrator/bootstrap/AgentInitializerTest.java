package com.aidevos.orchestrator.bootstrap;

import com.aidevos.orchestrator.manager.AgentManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class AgentInitializerTest {

	private final AgentManager agentManager;

	@Autowired
	AgentInitializerTest(AgentManager agentManager) {
		this.agentManager = agentManager;
	}

	@Test
	void shouldRegisterConfiguredAgentsOnStartup() {
		assertEquals(4, agentManager.getAllAgents().size());
		assertNotNull(agentManager.getAgent("planner"));
		assertNotNull(agentManager.getAgent("executor"));
		assertNotNull(agentManager.getAgent("coder"));
		assertNotNull(agentManager.getAgent("tester"));
	}
}
