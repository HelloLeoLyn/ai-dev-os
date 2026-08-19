package com.aidevos.orchestrator.modelregistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type",
	havingValue = "in-memory", matchIfMissing = true)
public class InMemoryModelDefinitionRepository implements ModelDefinitionRepository {

	private final Map<String, ModelDefinition> models = new LinkedHashMap<>();

	@Override
	public synchronized void save(ModelDefinition entity) {
		models.put(entity.getModelId(), entity);
	}

	@Override
	public synchronized ModelDefinition get(String id) {
		ModelDefinition existing = models.get(id);
		return existing == null ? null : copy(existing);
	}

	@Override
	public synchronized List<ModelDefinition> getAll() {
		return models.values().stream().map(this::copy).toList();
	}

	private ModelDefinition copy(ModelDefinition source) {
		ModelDefinition target = new ModelDefinition();
		target.setModelId(source.getModelId());
		target.setDisplayName(source.getDisplayName());
		target.setProviderId(source.getProviderId());
		target.setExecutorType(source.getExecutorType());
		target.setEnabled(source.isEnabled());
		target.setCapabilities(source.getCapabilities());
		return target;
	}
}
