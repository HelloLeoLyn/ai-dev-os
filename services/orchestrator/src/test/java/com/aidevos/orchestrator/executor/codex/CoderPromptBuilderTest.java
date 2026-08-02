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
}
