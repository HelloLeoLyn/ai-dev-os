package com.aidevos.orchestrator.modelrouter;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelConfigLoaderTest {

	private final ModelConfigLoader loader = new ModelConfigLoader();

	@Test
	void shouldLoadConfiguredProviders() {
		ModelConfig config = loader.load();

		assertEquals(4, config.providers().size());
		assertEquals("deepseek", config.providers().getFirst().getProviderId());
		assertEquals("DeepSeek", config.providers().getFirst().getName());
		assertEquals("deepseek-chat", config.providers().getFirst().getModel());
		assertEquals(true, config.providers().getFirst().isEnabled());
	}

	@Test
	void shouldLoadRoutes() {
		ModelConfig config = loader.load();

		assertEquals("deepseek", config.routes().get(TaskType.TASK_ANALYSIS));
		assertEquals("codex", config.routes().get(TaskType.CODE_GENERATION));
		assertEquals("openclaw", config.routes().get(TaskType.BROWSER_TEST));
		assertEquals("openai", config.routes().get(TaskType.GENERAL));
	}

	@Test
	void shouldRejectUnknownProviderInRoute() {
		Map<String, Object> config = Map.of(
			"providers", List.of(Map.of("providerId", "openai", "model", "gpt-4o")),
			"routes", Map.of("GENERAL", "missing-provider"));

		assertThrows(IllegalStateException.class, () -> loader.toModelConfig(config));
	}

	@Test
	void shouldRequireGeneralRoute() {
		Map<String, Object> config = Map.of(
			"providers", List.of(Map.of("providerId", "openai", "model", "gpt-4o")),
			"routes", Map.of("TASK_ANALYSIS", "openai"));

		assertThrows(IllegalStateException.class, () -> loader.toModelConfig(config));
	}
}
