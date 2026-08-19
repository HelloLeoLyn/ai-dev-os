package com.aidevos.orchestrator.modelregistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelResolverTest {

	private InMemoryProviderDefinitionRepository providers;
	private InMemoryModelDefinitionRepository models;
	private ModelResolver resolver;

	@BeforeEach
	void setUp() {
		providers = new InMemoryProviderDefinitionRepository();
		models = new InMemoryModelDefinitionRepository();
		resolver = new ModelResolver(providers, models);
	}

	private void seedDeepSeek() {
		ProviderDefinition provider = new ProviderDefinition();
		provider.setProviderId("deepseek");
		provider.setDisplayName("DeepSeek");
		provider.setBaseUrl("https://api.deepseek.com");
		provider.setCredentialRef("OPENAI_API_KEY");
		provider.setEnabled(true);
		providers.save(provider);

		ModelDefinition model = new ModelDefinition();
		model.setModelId("deepseek-v4-flash");
		model.setDisplayName("DeepSeek V4 Flash");
		model.setProviderId("deepseek");
		model.setExecutorType("codex");
		model.setEnabled(true);
		model.setCapabilities(java.util.List.of("coding"));
		models.save(model);
	}

	@Test
	void resolvesExplicitDeepSeekModel() {
		seedDeepSeek();
		ModelResolver envResolver = new ModelResolver(providers, models) {
			@Override
			protected String lookupEnv(String name) {
				return "test-value";
			}
		};

		ResolvedModel resolved = envResolver.resolve("deepseek-v4-flash", null);

		assertEquals("deepseek-v4-flash", resolved.requestedModelId());
		assertEquals("deepseek-v4-flash", resolved.resolvedModelId());
		assertEquals("deepseek", resolved.providerId());
		assertEquals("codex", resolved.executorType());
		assertEquals("https://api.deepseek.com", resolved.baseUrl());
		assertEquals("OPENAI_API_KEY", resolved.credentialRef());
	}

	@Test
	void resolvesAutoToAgentDefaultModel() {
		seedDeepSeek();
		ModelResolver envResolver = new ModelResolver(providers, models) {
			@Override
			protected String lookupEnv(String name) {
				return "test-value";
			}
		};

		ResolvedModel resolved = envResolver.resolve(null, "deepseek-v4-flash");

		assertEquals("AUTO", resolved.requestedModelId());
		assertEquals("deepseek-v4-flash", resolved.resolvedModelId());
		assertEquals("deepseek", resolved.providerId());
	}

	@Test
	void failsClosedWhenNoRequestedModelAndNoAgentDefault() {
		seedDeepSeek();
		ModelResolutionException exception = assertThrows(ModelResolutionException.class,
			() -> resolver.resolve(null, null));
		assertEquals(ModelResolutionException.Code.MODEL_NOT_FOUND, exception.code());
	}

	@Test
	void failsClosedWhenModelNotFound() {
		seedDeepSeek();
		ModelResolutionException exception = assertThrows(ModelResolutionException.class,
			() -> resolver.resolve("unknown-model", null));
		assertEquals(ModelResolutionException.Code.MODEL_NOT_FOUND, exception.code());
	}

	@Test
	void failsClosedWhenModelDisabled() {
		seedDeepSeek();
		ModelDefinition model = models.get("deepseek-v4-flash");
		model.setEnabled(false);
		models.save(model);

		ModelResolutionException exception = assertThrows(ModelResolutionException.class,
			() -> resolver.resolve("deepseek-v4-flash", null));
		assertEquals(ModelResolutionException.Code.MODEL_DISABLED, exception.code());
	}

	@Test
	void failsClosedWhenProviderNotFound() {
		seedDeepSeek();
		ModelDefinition model = models.get("deepseek-v4-flash");
		model.setProviderId("missing-provider");
		models.save(model);

		ModelResolutionException exception = assertThrows(ModelResolutionException.class,
			() -> resolver.resolve("deepseek-v4-flash", null));
		assertEquals(ModelResolutionException.Code.PROVIDER_NOT_FOUND, exception.code());
	}

	@Test
	void failsClosedWhenProviderDisabled() {
		seedDeepSeek();
		ProviderDefinition provider = providers.get("deepseek");
		provider.setEnabled(false);
		providers.save(provider);

		ModelResolutionException exception = assertThrows(ModelResolutionException.class,
			() -> resolver.resolve("deepseek-v4-flash", null));
		assertEquals(ModelResolutionException.Code.PROVIDER_DISABLED, exception.code());
	}

	@Test
	void failsClosedWhenCredentialMissing() {
		seedDeepSeek();
		ModelResolver noEnvResolver = new ModelResolver(providers, models) {
			@Override
			protected String lookupEnv(String name) {
				return null;
			}
		};
		ModelResolutionException exception = assertThrows(ModelResolutionException.class,
			() -> noEnvResolver.resolve("deepseek-v4-flash", null));
		assertEquals(ModelResolutionException.Code.CREDENTIAL_MISSING, exception.code());
	}

	@Test
	void failsClosedOnUnsupportedExecutor() {
		seedDeepSeek();
		ModelDefinition model = models.get("deepseek-v4-flash");
		model.setExecutorType("openclaw");
		models.save(model);

		ModelResolutionException exception = assertThrows(ModelResolutionException.class,
			() -> resolver.resolve("deepseek-v4-flash", null));
		assertEquals(ModelResolutionException.Code.UNSUPPORTED_EXECUTOR, exception.code());
	}

	@Test
	void requireExecutorRejectsMismatch() {
		seedDeepSeek();
		ResolvedModel resolved = new ResolvedModel("deepseek-v4-flash", "deepseek-v4-flash",
			"deepseek", "openclaw", null, null);

		ModelResolutionException exception = assertThrows(ModelResolutionException.class,
			() -> resolver.requireExecutor(resolved, "codex"));
		assertEquals(ModelResolutionException.Code.MODEL_EXECUTOR_MISMATCH, exception.code());
	}

	@Test
	void providerWithoutCredentialRefResolvesWithoutEnv() {
		ProviderDefinition provider = new ProviderDefinition();
		provider.setProviderId("openai");
		provider.setDisplayName("OpenAI");
		provider.setBaseUrl(null);
		provider.setCredentialRef(null);
		provider.setEnabled(true);
		providers.save(provider);

		ModelDefinition model = new ModelDefinition();
		model.setModelId("gpt-5.4");
		model.setDisplayName("GPT-5.4");
		model.setProviderId("openai");
		model.setExecutorType("codex");
		model.setEnabled(true);
		models.save(model);

		ResolvedModel resolved = resolver.resolve("gpt-5.4", null);
		assertEquals("gpt-5.4", resolved.resolvedModelId());
		assertNull(resolved.baseUrl());
		assertNull(resolved.credentialRef());
	}
}
