package com.aidevos.orchestrator.persistence.postgresql;

import java.util.List;

import com.aidevos.orchestrator.modelregistry.ModelDefinition;
import com.aidevos.orchestrator.modelregistry.ProviderDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresModelRegistryRepositoryTest {

	@Test
	void persistsProviderInRepositoryDocuments() {
		PostgresDocumentStore store = mock(PostgresDocumentStore.class);
		PostgresProviderDefinitionRepository repository = new PostgresProviderDefinitionRepository(store);

		ProviderDefinition provider = new ProviderDefinition();
		provider.setProviderId("deepseek");
		provider.setDisplayName("DeepSeek");
		provider.setBaseUrl("https://api.deepseek.com");
		provider.setCredentialRef("OPENAI_API_KEY");
		provider.setEnabled(true);

		when(store.get("model-provider", "deepseek", ProviderDefinition.class)).thenReturn(provider);
		when(store.all("model-provider", ProviderDefinition.class)).thenReturn(List.of(provider));

		repository.save(provider);
		verify(store).put("model-provider", "deepseek", provider, null);
		assertEquals("deepseek", repository.get("deepseek").getProviderId());
		assertEquals(1, repository.getAll().size());
	}

	@Test
	void persistsModelInRepositoryDocuments() {
		PostgresDocumentStore store = mock(PostgresDocumentStore.class);
		PostgresModelDefinitionRepository repository = new PostgresModelDefinitionRepository(store);

		ModelDefinition model = new ModelDefinition();
		model.setModelId("deepseek-v4-flash");
		model.setDisplayName("DeepSeek V4 Flash");
		model.setProviderId("deepseek");
		model.setExecutorType("codex");
		model.setEnabled(true);

		when(store.get("model-definition", "deepseek-v4-flash", ModelDefinition.class)).thenReturn(model);

		repository.save(model);
		verify(store).put("model-definition", "deepseek-v4-flash", model, null);
		assertEquals("deepseek-v4-flash", repository.get("deepseek-v4-flash").getModelId());
	}
}
