package com.aidevos.orchestrator.persistence.postgresql;

import java.util.List;

import com.aidevos.orchestrator.modelregistry.ModelDefinition;
import com.aidevos.orchestrator.modelregistry.ModelDefinitionRepository;

final class PostgresModelDefinitionRepository implements ModelDefinitionRepository {

	private static final String TYPE = "model-definition";

	private final PostgresDocumentStore store;

	PostgresModelDefinitionRepository(PostgresDocumentStore store) {
		this.store = store;
	}

	@Override
	public void save(ModelDefinition entity) {
		store.put(TYPE, entity.getModelId(), entity, null);
	}

	@Override
	public ModelDefinition get(String id) {
		return store.get(TYPE, id, ModelDefinition.class);
	}

	@Override
	public List<ModelDefinition> getAll() {
		return store.all(TYPE, ModelDefinition.class);
	}
}
