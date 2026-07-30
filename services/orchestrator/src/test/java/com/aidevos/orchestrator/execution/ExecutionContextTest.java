package com.aidevos.orchestrator.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExecutionContextTest {

	@Test
	void shouldCreateExecutionContext() {
		ExecutionContext context = new ExecutionContext();

		assertNotNull(context);
	}

	@Test
	void shouldStoreAndReturnProperties() {
		ExecutionContext context = new ExecutionContext();

		context.setTaskId("task-1");
		context.setTaskName("Implement executor");
		context.setDescription("Execute a task with a real executor");
		context.setAgentName("codex");
		context.setInput("Implement the requested change");
		context.setWorkspace("/workspace/project");

		assertEquals("task-1", context.getTaskId());
		assertEquals("Implement executor", context.getTaskName());
		assertEquals("Execute a task with a real executor", context.getDescription());
		assertEquals("codex", context.getAgentName());
		assertEquals("Implement the requested change", context.getInput());
		assertEquals("/workspace/project", context.getWorkspace());
	}
}
