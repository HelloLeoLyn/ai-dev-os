package com.aidevos.orchestrator.executor.codex;

import java.util.Map;

import com.aidevos.orchestrator.execution.ExecutionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CoderPromptBuilderTest {

	@Test
	void shouldIncludeOnlyStructuredInputsProvidedByScheduler() {
		ExecutionContext context = new ExecutionContext();
		context.setDescription("Fix login validation");
		context.setParameters(Map.of("inputs", Map.of("sourceContext",
			Map.of("type", "mcp-text", "content", "validation rule"))));

		String prompt = new CoderPromptBuilder().build(context);

		assertTrue(prompt.contains("sourceContext"));
		assertTrue(prompt.contains("validation rule"));
	}

	@Test
	void rendersOnlyTheScopedApprovedExecutionHandoff() {
		ExecutionContext context = new ExecutionContext();
		context.setDescription("Create the smoke test");
		ApprovedExecutionHandoff handoff = new ApprovedExecutionHandoff(
			"task-1", "plan-1", 1, "job-1", "/runtime/task-1", "approval-1",
			"CODING", "WORKSPACE_WRITE");

		String prompt = new CoderPromptBuilder().build(context, handoff);

		assertTrue(prompt.contains("This invocation is the approved execution phase."));
		assertTrue(prompt.contains("CODING / WORKSPACE_WRITE"));
		assertTrue(prompt.contains("/runtime/task-1"));
		assertTrue(prompt.contains("Continue to obey all other AGENTS.md rules"));
		assertTrue(prompt.contains("does NOT authorize unrelated operations"));
	}
}
