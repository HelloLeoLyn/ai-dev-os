package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexExecutorTest {

	@Test
	void shouldReturnCodexType() {
		assertEquals("codex", new CodexExecutor().getType());
	}

	@Test
	void shouldExecuteTaskWithSimulatedOutput() {
		ExecutionContext context = new ExecutionContext();
		context.setTaskId("task-1");
		context.setDescription("Implement a new feature");

		ExecutionResult result = new CodexExecutor().execute(context);

		assertTrue(result.isSuccess());
		assertEquals("Task executed successfully", result.getMessage());
		assertEquals("Simulated Codex execution for task task-1: Implement a new feature", result.getOutput());
	}
}
