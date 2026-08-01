package com.aidevos.orchestrator.execution;

import java.util.Map;

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

		context.setExecutionId("execution-1");
		context.setJobId("job-1");
		context.setTaskId("task-1");
		context.setTaskName("Implement executor");
		context.setDescription("Execute a task with a real executor");
		context.setAgentName("codex");
		context.setInput("Implement the requested change");
		context.setWorkspace("/workspace/project");
		context.setMetadata(Map.of("source", "api"));
		context.setParameters(Map.of("timeoutSeconds", 120));

		assertEquals("execution-1", context.getExecutionId());
		assertEquals("job-1", context.getJobId());
		assertEquals("task-1", context.getTaskId());
		assertEquals("Implement executor", context.getTaskName());
		assertEquals("Execute a task with a real executor", context.getDescription());
		assertEquals("codex", context.getAgentName());
		assertEquals("Implement the requested change", context.getInput());
		assertEquals("/workspace/project", context.getWorkspace());
		assertEquals(Map.of("source", "api"), context.getMetadata());
		assertEquals(Map.of("timeoutSeconds", 120), context.getParameters());
	}

	@Test
	void shouldInitializeMetadataAndParameters() {
		ExecutionContext context = new ExecutionContext();

		assertNotNull(context.getMetadata());
		assertNotNull(context.getParameters());
	}
}
