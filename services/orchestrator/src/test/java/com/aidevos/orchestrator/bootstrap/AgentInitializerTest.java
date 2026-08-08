package com.aidevos.orchestrator.bootstrap;

import com.aidevos.orchestrator.manager.AgentManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = "aidevos.persistence.type=in-memory")
@ActiveProfiles("test")
class AgentInitializerTest {

	private final AgentManager agentManager;

	@Autowired
	AgentInitializerTest(AgentManager agentManager) {
		this.agentManager = agentManager;
	}

	@Test
	void shouldRegisterConfiguredAgentsOnStartup() {
		assertEquals(6, agentManager.getAllAgents().size());
		assertNotNull(agentManager.getAgent("planner"));
		assertNotNull(agentManager.getAgent("executor"));
		assertNotNull(agentManager.getAgent("coder"));
		assertNotNull(agentManager.getAgent("tester"));
		assertEquals("openclaw", agentManager.getAgent("browser-agent").getExecutor());
		assertEquals("main", agentManager.getAgent("browser-agent").getExternalId());
		assertEquals("tool", agentManager.getAgent("mcp-reader").getExecutor());
	}
}
