package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockAgentExecutorTest {

	@Test
	void shouldExecuteTaskWithSimulatedOutput() {
		ExecutionContext context = new ExecutionContext();
		context.setTaskId("task-1");
		context.setDescription("Create an implementation plan");

		ExecutionResult result = new MockAgentExecutor().execute(context);

		assertTrue(result.isSuccess());
		assertEquals("Task executed successfully", result.getMessage());
		assertEquals("Simulated execution for task task-1: Create an implementation plan", result.getOutput());
	}
}
