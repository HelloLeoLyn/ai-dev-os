package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockAgentExecutorTest {

	@Test
	void shouldExecuteTaskWithSimulatedOutput() {
		TaskDefinition taskDefinition = new TaskDefinition();
		taskDefinition.setId("task-1");
		taskDefinition.setDescription("Create an implementation plan");

		ExecutionResult result = new MockAgentExecutor().execute(taskDefinition);

		assertTrue(result.isSuccess());
		assertEquals("Task executed successfully", result.getMessage());
		assertEquals("Simulated execution for task task-1: Create an implementation plan", result.getOutput());
	}
}
