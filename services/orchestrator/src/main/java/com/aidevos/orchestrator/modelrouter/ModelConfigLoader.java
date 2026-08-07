package com.aidevos.orchestrator.modelrouter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.common.config.YamlConfigSupport;
import org.springframework.stereotype.Component;

/**
 * Loads models.yaml into a ModelConfig. YAML reading, conversions and
 * validation helpers come from YamlConfigSupport; read-only configuration used
 * by the routing layer.
 */
@Component
public class ModelConfigLoader {

	private static final String CONFIG_FILE = "models.yaml";

	public ModelConfig load() {
		return toModelConfig(YamlConfigSupport.load(CONFIG_FILE));
	}

	ModelConfig toModelConfig(Map<String, Object> config) {
		List<ModelProvider> providers = toProviders(config.get("providers"));
		Map<TaskType, String> routes = toRoutes(config.get("routes"), providers);
		return new ModelConfig(providers, routes);
	}

	private List<ModelProvider> toProviders(Object value) {
		List<ModelProvider> providers = new ArrayList<>();
		Set<String> providerIds = new HashSet<>();
		for (Map<String, Object> map : YamlConfigSupport.asList(value, "providers", "provider")) {
			ModelProvider provider = toProvider(map);
			YamlConfigSupport.require(provider.getProviderId(), "providerId");
			YamlConfigSupport.requireUnique(providerIds, provider.getProviderId(), "providerId");
			providers.add(provider);
		}
		if (providers.isEmpty()) {
			throw new IllegalStateException("At least one model provider is required");
		}
		return providers;
	}

	private ModelProvider toProvider(Map<String, Object> map) {
		ModelProvider provider = new ModelProvider();
		provider.setProviderId(YamlConfigSupport.string(map, "providerId"));
		provider.setName(YamlConfigSupport.string(map, "name"));
		provider.setType(YamlConfigSupport.string(map, "type"));
		provider.setModel(YamlConfigSupport.string(map, "model"));
		provider.setEnabled(YamlConfigSupport.bool(map, "enabled", false));
		return provider;
	}

	private Map<TaskType, String> toRoutes(Object value, List<ModelProvider> providers) {
		Map<String, Object> routeValues = YamlConfigSupport.asMap(value, "routes");
		Set<String> providerIds = new HashSet<>();
		for (ModelProvider provider : providers) {
			providerIds.add(provider.getProviderId());
		}
		Map<TaskType, String> routes = new HashMap<>();
		for (Map.Entry<String, Object> entry : routeValues.entrySet()) {
			if (!(entry.getValue() instanceof String providerId)) {
				throw new IllegalStateException("Invalid route definition");
			}
			TaskType taskType = TaskType.from(entry.getKey());
			if (routes.containsKey(taskType)) {
				throw new IllegalStateException("Duplicate route for task type: " + taskType);
			}
			if (!providerIds.contains(providerId)) {
				throw new IllegalStateException("Route targets unknown provider: " + providerId);
			}
			routes.put(taskType, providerId);
		}
		if (!routes.containsKey(TaskType.GENERAL)) {
			throw new IllegalStateException("GENERAL route is required");
		}
		return routes;
	}
}
