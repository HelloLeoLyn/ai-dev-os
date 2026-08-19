package com.aidevos.orchestrator.modelregistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type",
	havingValue = "in-memory", matchIfMissing = true)
public class InMemoryProviderDefinitionRepository implements ProviderDefinitionRepository {

	private final Map<String, ProviderDefinition> providers = new LinkedHashMap<>();

	@Override
	public synchronized void save(ProviderDefinition entity) {
		providers.put(entity.getProviderId(), entity);
	}

	@Override
	public synchronized ProviderDefinition get(String id) {
		ProviderDefinition existing = providers.get(id);
		return existing == null ? null : copy(existing);
	}

	@Override
	public synchronized List<ProviderDefinition> getAll() {
		return providers.values().stream().map(this::copy).toList();
	}

	private ProviderDefinition copy(ProviderDefinition source) {
		ProviderDefinition target = new ProviderDefinition();
		target.setProviderId(source.getProviderId());
		target.setDisplayName(source.getDisplayName());
		target.setBaseUrl(source.getBaseUrl());
		target.setCredentialRef(source.getCredentialRef());
		target.setEnabled(source.isEnabled());
		return target;
	}
}
