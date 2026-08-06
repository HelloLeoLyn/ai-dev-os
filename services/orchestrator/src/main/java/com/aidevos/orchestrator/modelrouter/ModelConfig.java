package com.aidevos.orchestrator.modelrouter;

import java.util.List;
import java.util.Map;

/**
 * Parsed models.yaml content: configured providers and task-type routing.
 */
public record ModelConfig(
		List<ModelProvider> providers,
		Map<TaskType, String> routes) {

	public ModelConfig {
		providers = List.copyOf(providers);
		routes = Map.copyOf(routes);
	}
}
