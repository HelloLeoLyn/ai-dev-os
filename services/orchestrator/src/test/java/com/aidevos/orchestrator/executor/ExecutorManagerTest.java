package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExecutorManagerTest {

	@Test
	void shouldReturnMockExecutorForRegisteredAgent() {
		AgentManager agentManager = new AgentManager();
		AgentDefinition agentDefinition = new AgentDefinition();
		agentDefinition.setName("planner");
		agentDefinition.setExecutor("mock");
		agentManager.register(agentDefinition);
		MockAgentExecutor mockAgentExecutor = new MockAgentExecutor();
		ExecutorManager executorManager = new ExecutorManager(agentManager, mockAgentExecutor);

		assertSame(mockAgentExecutor, executorManager.getExecutor("planner"));
	}

	@Test
	void shouldReturnNullWhenExecutorTypeIsUnknown() {
		AgentManager agentManager = new AgentManager();
		AgentDefinition agentDefinition = new AgentDefinition();
		agentDefinition.setName("planner");
		agentDefinition.setExecutor("unknown");
		agentManager.register(agentDefinition);
		ExecutorManager executorManager = new ExecutorManager(agentManager, new MockAgentExecutor());

		assertNull(executorManager.getExecutor("planner"));
	}

	@Test
	void shouldReturnNullWhenExecutorDoesNotExist() {
		ExecutorManager executorManager = new ExecutorManager(new AgentManager(), new MockAgentExecutor());

		assertNull(executorManager.getExecutor("unknown"));
	}
}
