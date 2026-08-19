package com.aidevos.orchestrator.modelregistry;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelRegistryServiceTest {

	private ModelRegistryService service;
	private ModelConfigFixture fixture;

	@BeforeEach
	void setUp() {
		service = new ModelRegistryService(new InMemoryProviderDefinitionRepository(),
			new InMemoryModelDefinitionRepository());
		fixture = new ModelConfigFixture();
	}

	@Test
	void seedsProvidersAndModelsFromConfig() {
		int seeded = service.seedFrom(fixture.config());

		assertTrue(seeded > 0);
		ProviderDefinition deepseek = service.getProvider("deepseek");
		assertNotNull(deepseek);
		assertEquals("DeepSeek", deepseek.getDisplayName());
		assertTrue(deepseek.isEnabled());
		assertEquals("https://api.deepseek.com", deepseek.getBaseUrl());
		assertEquals("DEEPSEEK_API_KEY", deepseek.getCredentialRef());

		ModelDefinition model = service.getModel("deepseek-chat");
		assertNotNull(model);
		assertEquals("deepseek", model.getProviderId());
		assertEquals("codex", model.getExecutorType());

		ModelDefinition flash = service.getModel("deepseek-v4-flash");
		assertNotNull(flash);
		assertEquals("deepseek", flash.getProviderId());
		assertEquals("codex", flash.getExecutorType());
		assertTrue(flash.isEnabled());
	}

	@Test
	void seedIsIdempotentAndNeverOverwritesExisting() {
		service.seedFrom(fixture.config());

		ProviderDefinition deepseek = service.getProvider("deepseek");
		deepseek.setEnabled(false);
		deepseek.setBaseUrl("https://custom.example.com");
		deepseek.setCredentialRef("DEEPSEEK_API_KEY");
		service.updateProvider("deepseek", deepseek);

		int reseeded = service.seedFrom(fixture.config());
		assertEquals(0, reseeded);

		ProviderDefinition after = service.getProvider("deepseek");
		assertFalse(after.isEnabled());
		assertEquals("https://custom.example.com", after.getBaseUrl());
		assertEquals("DEEPSEEK_API_KEY", after.getCredentialRef());
	}

	@Test
	void createAndUpdateProviderRoundTrip() {
		ProviderDefinition provider = new ProviderDefinition();
		provider.setProviderId("deepseek");
		provider.setDisplayName("DeepSeek");
		provider.setBaseUrl("https://api.deepseek.com");
		provider.setCredentialRef("OPENAI_API_KEY");
		provider.setEnabled(true);

		ProviderDefinition created = service.createProvider(provider);
		assertEquals("https://api.deepseek.com", created.getBaseUrl());
		assertEquals("OPENAI_API_KEY", created.getCredentialRef());

		ProviderDefinition existing = service.getProvider("deepseek");
		assertNotNull(existing);

		existing.setDisplayName("DeepSeek V2");
		ProviderDefinition updated = service.updateProvider("deepseek", existing);
		assertEquals("DeepSeek V2", updated.getDisplayName());

		assertThrows(IllegalStateException.class,
			() -> service.createProvider(provider));
		assertThrows(IllegalArgumentException.class,
			() -> service.updateProvider("missing", existing));
	}

	@Test
	void enableDisableProviderToggle() {
		service.seedFrom(fixture.config());

		ProviderDefinition disabled = service.setProviderEnabled("deepseek", false);
		assertFalse(disabled.isEnabled());

		ProviderDefinition reEnabled = service.setProviderEnabled("deepseek", true);
		assertTrue(reEnabled.isEnabled());

		assertThrows(IllegalArgumentException.class,
			() -> service.setProviderEnabled("unknown", true));
	}

	@Test
	void createModelRequiresExistingProvider() {
		service.seedFrom(fixture.config());

		ModelDefinition model = new ModelDefinition();
		model.setModelId("deepseek-reasoner");
		model.setDisplayName("DeepSeek Reasoner");
		model.setProviderId("deepseek");
		model.setExecutorType("codex");
		model.setEnabled(true);

		ModelDefinition created = service.createModel(model);
		assertEquals("deepseek-reasoner", created.getModelId());

		ModelDefinition withoutProvider = new ModelDefinition();
		withoutProvider.setModelId("ghost-model");
		withoutProvider.setDisplayName("Ghost");
		withoutProvider.setProviderId("ghost-provider");
		withoutProvider.setExecutorType("codex");
		assertThrows(IllegalArgumentException.class,
			() -> service.createModel(withoutProvider));

		ModelDefinition disabled = service.setModelEnabled("deepseek-reasoner", false);
		assertFalse(disabled.isEnabled());
	}

	@Test
	void rejectsInvalidCredentialRefAndBaseUrl() {
		ProviderDefinition provider = new ProviderDefinition();
		provider.setProviderId("deepseek");
		provider.setDisplayName("DeepSeek");
		provider.setBaseUrl("not-a-url");
		provider.setCredentialRef("DEEPSEEK_API_KEY");
		assertThrows(IllegalArgumentException.class,
			() -> service.createProvider(provider));

		provider.setBaseUrl("https://api.deepseek.com");
		provider.setCredentialRef("api key value");
		assertThrows(IllegalArgumentException.class,
			() -> service.createProvider(provider));

		provider.setCredentialRef("DEEPSEEK_API_KEY");
		ProviderDefinition created = service.createProvider(provider);
		assertEquals("DEEPSEEK_API_KEY", created.getCredentialRef());
	}

	@Test
	void registryNeverContainsSecretValues() {
		service.seedFrom(fixture.config());

		List<ProviderDefinition> providers = service.listProviders();
		for (ProviderDefinition provider : providers) {
			String credentialRef = provider.getCredentialRef();
			if (credentialRef != null) {
				assertTrue(credentialRef.matches("[A-Z][A-Z0-9_]*"),
					"credentialRef must be a name, not a secret value");
			}
			assertFalse(String.valueOf(provider.getDisplayName()).contains("sk-"));
		}
		for (java.lang.reflect.Field field : ProviderDefinition.class.getDeclaredFields()) {
			String name = field.getName().toLowerCase();
			assertFalse(name.contains("apikey") || name.contains("secret") || name.contains("token"),
				"ProviderDefinition must not carry secret fields: " + field.getName());
		}
		for (java.lang.reflect.Field field : ModelDefinition.class.getDeclaredFields()) {
			String name = field.getName().toLowerCase();
			assertFalse(name.contains("apikey") || name.contains("secret") || name.contains("token"),
				"ModelDefinition must not carry secret fields: " + field.getName());
		}
	}

	@Test
	void deepSeekV4FlashExampleIsConfigurable() {
		service.seedFrom(fixture.config());

		ProviderDefinition provider = service.getProvider("deepseek");
		provider.setBaseUrl("https://api.deepseek.com");
		provider.setCredentialRef("OPENAI_API_KEY");
		service.updateProvider("deepseek", provider);

		ModelDefinition model = new ModelDefinition();
		model.setModelId("deepseek-v4-mini");
		model.setDisplayName("DeepSeek V4 Mini");
		model.setProviderId("deepseek");
		model.setExecutorType("codex");
		model.setEnabled(true);
		service.createModel(model);

		ProviderDefinition saved = service.getProvider("deepseek");
		assertEquals("https://api.deepseek.com", saved.getBaseUrl());
		assertEquals("OPENAI_API_KEY", saved.getCredentialRef());
		assertEquals("deepseek-v4-mini", service.getModel("deepseek-v4-mini").getModelId());
	}

	@Test
	void providerStatusReportsCredentialConfiguredWithoutSecretValue() {
		ProviderDefinition provider = new ProviderDefinition();
		provider.setProviderId("deepseek");
		provider.setDisplayName("DeepSeek");
		provider.setBaseUrl("https://api.deepseek.com");
		provider.setCredentialRef("DEEPSEEK_API_KEY");
		provider.setEnabled(true);
		service.createProvider(provider);

		ModelRegistryService statusService = new ModelRegistryService(
			new InMemoryProviderDefinitionRepository(), new InMemoryModelDefinitionRepository()) {
			@Override
			protected String environmentValue(String name) {
				return "DEEPSEEK_API_KEY".equals(name) ? "set" : null;
			}
		};
		statusService.createProvider(provider);

		ModelRegistryService.ProviderStatus configured =
			statusService.providerStatus("deepseek");
		assertEquals("DEEPSEEK_API_KEY", configured.credentialRef());
		assertTrue(configured.credentialConfigured());
		assertFalse(configured.toString().contains("set"));

		ModelRegistryService.ProviderStatus missing = service.providerStatus("deepseek");
		assertEquals("DEEPSEEK_API_KEY", missing.credentialRef());
		assertFalse(missing.credentialConfigured());
	}
}
