package com.aidevos.orchestrator.persistence.postgresql;

import java.util.List;

import com.aidevos.orchestrator.modelregistry.ProviderDefinition;
import com.aidevos.orchestrator.modelregistry.ProviderDefinitionRepository;

final class PostgresProviderDefinitionRepository implements ProviderDefinitionRepository {

	private static final String TYPE = "model-provider";

	private final PostgresDocumentStore store;

	PostgresProviderDefinitionRepository(PostgresDocumentStore store) {
		this.store = store;
	}

	@Override
	public void save(ProviderDefinition entity) {
		store.put(TYPE, entity.getProviderId(), entity, null);
	}

	@Override
	public ProviderDefinition get(String id) {
		return store.get(TYPE, id, ProviderDefinition.class);
	}

	@Override
	public List<ProviderDefinition> getAll() {
		return store.all(TYPE, ProviderDefinition.class);
	}
}
