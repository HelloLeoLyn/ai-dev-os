package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.config.AgentConfigLoader;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.modelregistry.ModelDefinition;
import com.aidevos.orchestrator.modelregistry.ModelRegistryService;
import com.aidevos.orchestrator.modelregistry.ProviderDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelRegistryControllerTest {

	private final ModelRegistryService registry = mock(ModelRegistryService.class);
	private final AgentConfigLoader agentConfigLoader = mock(AgentConfigLoader.class);
	private final ModelRegistryController controller = new ModelRegistryController(registry,
		agentConfigLoader);

	@Test
	void listsProvidersWithoutSecretValues() {
		ProviderDefinition provider = new ProviderDefinition();
		provider.setProviderId("deepseek");
		provider.setDisplayName("DeepSeek");
		provider.setBaseUrl("https://api.deepseek.com");
		provider.setCredentialRef("OPENAI_API_KEY");
		provider.setEnabled(true);
		when(registry.listProviders()).thenReturn(List.of(provider));

		List<ProviderDefinition> providers = controller.listProviders();
		assertEquals(1, providers.size());
		assertEquals("OPENAI_API_KEY", providers.getFirst().getCredentialRef());
		assertTrue(!providers.getFirst().toString().contains("sk-"));
	}

	@Test
	void createsProviderAndReturnsCreatedStatus() {
		ProviderDefinition provider = new ProviderDefinition();
		provider.setProviderId("deepseek");
		provider.setDisplayName("DeepSeek");
		when(registry.createProvider(provider)).thenReturn(provider);

		ResponseEntity<?> response = controller.createProvider(provider);
		assertEquals(201, response.getStatusCode().value());
		assertEquals("deepseek", ((ProviderDefinition) response.getBody()).getProviderId());
	}

	@Test
	void togglesProviderEnabled() {
		ProviderDefinition provider = new ProviderDefinition();
		provider.setProviderId("deepseek");
		provider.setEnabled(false);
		when(registry.setProviderEnabled("deepseek", false)).thenReturn(provider);

		ResponseEntity<?> response = controller.setProviderEnabled("deepseek",
			new ModelRegistryController.EnableRequest(false));
		assertEquals(200, response.getStatusCode().value());
		assertTrue(!((ProviderDefinition) response.getBody()).isEnabled());
		verify(registry).setProviderEnabled("deepseek", false);
	}

	@Test
	void createsModelLinkedToProvider() {
		ModelDefinition model = new ModelDefinition();
		model.setModelId("deepseek-v4-flash");
		model.setDisplayName("DeepSeek V4 Flash");
		model.setProviderId("deepseek");
		model.setExecutorType("codex");
		model.setEnabled(true);
		when(registry.createModel(model)).thenReturn(model);

		ResponseEntity<?> response = controller.createModel(model);
		assertEquals(201, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals("deepseek-v4-flash", ((ModelDefinition) response.getBody()).getModelId());
	}

	@Test
	void defaultModelReturnsCoderAgentConfiguredModel() {
		AgentDefinition coder = new AgentDefinition();
		coder.setName("coder");
		coder.setExecutor("codex");
		coder.setExecutorConfig(java.util.Map.of("model", "deepseek-v4-flash"));
		when(agentConfigLoader.loadAgents()).thenReturn(List.of(coder));

		ModelRegistryController.DefaultModelResponse response = controller.defaultModel();

		assertEquals("deepseek-v4-flash", response.modelId());
	}

	@Test
	void providerStatusReturnsCredentialReferenceWithoutSecret() {
		when(registry.providerStatus("deepseek")).thenReturn(
			new ModelRegistryService.ProviderStatus("deepseek", "DEEPSEEK_API_KEY", false));

		ResponseEntity<ModelRegistryService.ProviderStatus> response =
			controller.providerStatus("deepseek");

		assertEquals(200, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals("DEEPSEEK_API_KEY", response.getBody().credentialRef());
		assertTrue(!response.getBody().toString().contains("sk-"));
	}

	private ProviderDefinition anyProvider() {
		return new ProviderDefinition();
	}

	@Test
	void mapsValidationErrorsToBadRequest() {
		when(registry.createProvider(any(ProviderDefinition.class)))
			.thenThrow(new IllegalArgumentException("providerId is required"));

		ResponseEntity<?> response = controller.createProvider(anyProvider());
		assertEquals(400, response.getStatusCode().value());
	}
}
