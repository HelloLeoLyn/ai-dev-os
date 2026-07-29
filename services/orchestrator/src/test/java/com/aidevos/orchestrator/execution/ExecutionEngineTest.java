package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionEngineTest {

	@Test
	void shouldExecuteSuccessfullyWhenAgentExists() {
		AgentManager agentManager = new AgentManager();
		AgentDefinition agentDefinition = new AgentDefinition();
		agentDefinition.setName("planner");
		agentManager.register(agentDefinition);
		ExecutionEngine executionEngine = new ExecutionEngine(agentManager);
		TaskDefinition taskDefinition = createTask("planner");

		ExecutionResult result = executionEngine.execute(taskDefinition);

		assertTrue(result.isSuccess());
		assertEquals("Task executed successfully", result.getMessage());
		assertEquals("Simulated execution for task task-1: Create an implementation plan", result.getOutput());
		assertEquals("pending", taskDefinition.getStatus());
	}

	@Test
	void shouldFailWhenAgentDoesNotExist() {
		ExecutionEngine executionEngine = new ExecutionEngine(new AgentManager());
		TaskDefinition taskDefinition = createTask("unknown");

		ExecutionResult result = executionEngine.execute(taskDefinition);

		assertFalse(result.isSuccess());
		assertEquals("Agent not found: unknown", result.getMessage());
		assertNull(result.getOutput());
		assertEquals("pending", taskDefinition.getStatus());
	}

	private TaskDefinition createTask(String agentName) {
		TaskDefinition taskDefinition = new TaskDefinition();
		taskDefinition.setId("task-1");
		taskDefinition.setDescription("Create an implementation plan");
		taskDefinition.setAgentName(agentName);
		taskDefinition.setStatus("pending");
		return taskDefinition;
	}
}
