package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.executor.MockAgentExecutor;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionEngineTest {

	@Test
	void shouldExecuteSuccessfullyWhenAgentExists() {
		AgentManager agentManager = new AgentManager();
		AgentDefinition agentDefinition = new AgentDefinition();
		agentDefinition.setName("planner");
		agentDefinition.setExecutor("mock");
		agentManager.register(agentDefinition);
		ExecutionRecordManager executionRecordManager = new ExecutionRecordManager();
		ExecutionEngine executionEngine = new ExecutionEngine(
			new ExecutorManager(agentManager, new MockAgentExecutor()), executionRecordManager);
		TaskDefinition taskDefinition = createTask("planner");

		ExecutionResult result = executionEngine.execute(taskDefinition);

		assertTrue(result.isSuccess());
		assertEquals("Task executed successfully", result.getMessage());
		assertEquals("Simulated execution for task task-1: Create an implementation plan", result.getOutput());
		assertEquals("pending", taskDefinition.getStatus());

		assertEquals(1, executionRecordManager.getAll().size());
		ExecutionRecord record = executionRecordManager.getAll().get(0);
		assertNotNull(record.getId());
		assertEquals("task-1", record.getTaskId());
		assertEquals("planner", record.getAgentName());
		assertEquals("SUCCESS", record.getStatus());
		assertEquals(result.getMessage(), record.getMessage());
		assertEquals(result.getOutput(), record.getOutput());
	}

	@Test
	void shouldFailWhenAgentDoesNotExist() {
		ExecutionRecordManager executionRecordManager = new ExecutionRecordManager();
		ExecutionEngine executionEngine = new ExecutionEngine(
			new ExecutorManager(new AgentManager(), new MockAgentExecutor()), executionRecordManager);
		TaskDefinition taskDefinition = createTask("unknown");

		ExecutionResult result = executionEngine.execute(taskDefinition);

		assertFalse(result.isSuccess());
		assertEquals("Agent not found: unknown", result.getMessage());
		assertNull(result.getOutput());
		assertEquals("pending", taskDefinition.getStatus());

		assertEquals(1, executionRecordManager.getAll().size());
		ExecutionRecord record = executionRecordManager.getAll().get(0);
		assertNotNull(record.getId());
		assertEquals("task-1", record.getTaskId());
		assertEquals("unknown", record.getAgentName());
		assertEquals("FAILED", record.getStatus());
		assertEquals(result.getMessage(), record.getMessage());
		assertNull(record.getOutput());
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
