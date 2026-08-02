package com.aidevos.orchestrator.browser;

import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserTaskPromptBuilderTest {

	@Test
	void shouldIncludeExplicitPredecessorArtifactInputs() {
		BrowserTaskPromptBuilder builder = new BrowserTaskPromptBuilder(JsonMapper.builder().build());

		String prompt = builder.build("Verify login", Map.of(
			"browser", Map.of("action", "navigate", "url", "https://example.com"),
			"inputs", Map.of("codeChanges", Map.of("type", "git-diff",
				"content", "login fix"))));

		assertTrue(prompt.contains("codeChanges"));
		assertTrue(prompt.contains("login fix"));
	}
}
