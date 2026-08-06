package com.aidevos.orchestrator.modelrouter;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads models.yaml into a ModelConfig. Mirrors AgentConfigLoader; read-only
 * configuration used by the routing layer.
 */
@Component
public class ModelConfigLoader {

	private static final String CONFIG_FILE = "models.yaml";

	public ModelConfig load() {
		try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
			if (inputStream == null) {
				throw new IllegalStateException("Configuration file not found: " + CONFIG_FILE);
			}
			Map<String, Object> config = new Yaml().load(inputStream);
			return toModelConfig(config);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to read configuration file: " + CONFIG_FILE, exception);
		}
	}

	ModelConfig toModelConfig(Map<String, Object> config) {
		List<ModelProvider> providers = toProviders(config.get("providers"));
		Map<TaskType, String> routes = toRoutes(config.get("routes"), providers);
		return new ModelConfig(providers, routes);
	}

	private List<ModelProvider> toProviders(Object value) {
		if (!(value instanceof List<?> providerValues)) {
			throw new IllegalStateException("Invalid providers configuration");
		}
		List<ModelProvider> providers = new ArrayList<>();
		Set<String> providerIds = new HashSet<>();
		for (Object providerValue : providerValues) {
			if (!(providerValue instanceof Map<?, ?> map)) {
				throw new IllegalStateException("Invalid provider definition");
			}
			ModelProvider provider = toProvider(map);
			if (isBlank(provider.getProviderId())) {
				throw new IllegalStateException("providerId is required");
			}
			if (!providerIds.add(provider.getProviderId())) {
				throw new IllegalStateException("Duplicate providerId: " + provider.getProviderId());
			}
			providers.add(provider);
		}
		if (providers.isEmpty()) {
			throw new IllegalStateException("At least one model provider is required");
		}
		return providers;
	}

	private ModelProvider toProvider(Map<?, ?> map) {
		ModelProvider provider = new ModelProvider();
		provider.setProviderId(stringValue(map.get("providerId")));
		provider.setName(stringValue(map.get("name")));
		provider.setType(stringValue(map.get("type")));
		provider.setModel(stringValue(map.get("model")));
		if (map.containsKey("enabled")) {
			provider.setEnabled(Boolean.TRUE.equals(map.get("enabled")));
		}
		return provider;
	}

	private Map<TaskType, String> toRoutes(Object value, List<ModelProvider> providers) {
		if (!(value instanceof Map<?, ?> routeValues)) {
			throw new IllegalStateException("Invalid routes configuration");
		}
		Set<String> providerIds = new HashSet<>();
		for (ModelProvider provider : providers) {
			providerIds.add(provider.getProviderId());
		}
		Map<TaskType, String> routes = new HashMap<>();
		for (Map.Entry<?, ?> entry : routeValues.entrySet()) {
			if (!(entry.getKey() instanceof String taskTypeValue)
					|| !(entry.getValue() instanceof String providerId)) {
				throw new IllegalStateException("Invalid route definition");
			}
			TaskType taskType = TaskType.from(taskTypeValue);
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

	private String stringValue(Object value) {
		return value instanceof String string ? string : null;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
