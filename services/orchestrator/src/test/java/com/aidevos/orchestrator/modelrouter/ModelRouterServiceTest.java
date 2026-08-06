package com.aidevos.orchestrator.modelrouter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelRouterServiceTest {

	private ModelRouterService service;

	@BeforeEach
	void setUp() {
		service = new ModelRouterService(new ModelConfigLoader());
	}

	@Test
	void shouldLoadConfiguredProviders() {
		List<ModelProvider> providers = service.listProviders();

		assertEquals(4, providers.size());
		assertEquals("deepseek", providers.getFirst().getProviderId());
		assertEquals("openai", providers.get(1).getProviderId());
		assertEquals("codex", providers.get(2).getProviderId());
		assertEquals("openclaw", providers.get(3).getProviderId());
	}

	@Test
	void shouldRouteTaskAnalysisToDeepSeek() {
		ResolvedModel model = service.route(TaskType.TASK_ANALYSIS);

		assertEquals(TaskType.TASK_ANALYSIS, model.taskType());
		assertEquals("deepseek", model.providerId());
		assertEquals("DeepSeek", model.providerName());
		assertEquals("deepseek-chat", model.model());
		assertTrue(model.enabled());
	}

	@Test
	void shouldRouteCodeGenerationToCodex() {
		ResolvedModel model = service.route(TaskType.CODE_GENERATION);

		assertEquals("codex", model.providerId());
		assertEquals("codex", model.model());
	}

	@Test
	void shouldRouteBrowserTestToOpenClaw() {
		ResolvedModel model = service.route(TaskType.BROWSER_TEST);

		assertEquals("openclaw", model.providerId());
	}

	@Test
	void shouldRouteGeneralToDefaultModel() {
		ResolvedModel model = service.route(TaskType.GENERAL);

		assertEquals(TaskType.GENERAL, model.taskType());
		assertEquals("openai", model.providerId());
		assertEquals("gpt-4o", model.model());
	}

	@Test
	void shouldFallBackToGeneralForUnknownTaskType() {
		ResolvedModel model = service.route("UNKNOWN_TASK");

		assertEquals(TaskType.GENERAL, model.taskType());
		assertEquals("openai", model.providerId());
	}

	@Test
	void shouldListRouteRules() {
		List<ModelRoute> routes = service.listRoutes();

		assertEquals(4, routes.size());
		ModelRoute general = routes.stream()
			.filter(route -> "GENERAL".equals(route.taskType()))
			.findFirst()
			.orElseThrow();
		assertEquals("openai", general.providerId());
		assertEquals("gpt-4o", general.model());
		assertTrue(general.enabled());
	}
}
