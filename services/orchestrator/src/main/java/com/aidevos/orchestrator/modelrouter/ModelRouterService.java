package com.aidevos.orchestrator.modelrouter;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Routing layer for choosing a provider/model by task type. Read-only: it does
 * not execute tasks and leaves ExecutionEngine / agent execution / scheduler
 * untouched. Unknown task types fall back to GENERAL, and a disabled or missing
 * provider falls back to the first enabled provider.
 */
@Service
public class ModelRouterService {

	private final Map<String, ModelProvider> providersById = new LinkedHashMap<>();
	private final Map<TaskType, String> routes = new LinkedHashMap<>();

	public ModelRouterService(ModelConfigLoader loader) {
		ModelConfig config = loader.load();
		for (ModelProvider provider : config.providers()) {
			providersById.put(provider.getProviderId(), provider);
		}
		for (Map.Entry<TaskType, String> route : config.routes().entrySet()) {
			routes.put(route.getKey(), route.getValue());
		}
	}

	public List<ModelProvider> listProviders() {
		return List.copyOf(providersById.values());
	}

	public List<ModelRoute> listRoutes() {
		List<ModelRoute> routeList = new ArrayList<>();
		for (TaskType taskType : TaskType.values()) {
			String providerId = routes.get(taskType);
			if (providerId == null) {
				continue;
			}
			ModelProvider provider = providersById.get(providerId);
			routeList.add(new ModelRoute(taskType.name(), providerId,
				provider == null ? null : provider.getModel(),
				provider != null && provider.isEnabled()));
		}
		return routeList;
	}

	public ResolvedModel route(TaskType taskType) {
		TaskType resolvedType = taskType == null ? TaskType.GENERAL : taskType;
		String providerId = routes.get(resolvedType);
		if (providerId == null) {
			providerId = routes.get(TaskType.GENERAL);
			resolvedType = TaskType.GENERAL;
		}
		ModelProvider provider = providerId == null ? null : providersById.get(providerId);
		if (provider == null || !provider.isEnabled()) {
			provider = firstEnabledProvider();
			resolvedType = TaskType.GENERAL;
		}
		return new ResolvedModel(resolvedType, provider.getProviderId(), provider.getName(),
			provider.getType(), provider.getModel(), provider.isEnabled());
	}

	public ResolvedModel route(String taskType) {
		return route(TaskType.from(taskType));
	}

	private ModelProvider firstEnabledProvider() {
		return providersById.values().stream()
			.filter(ModelProvider::isEnabled)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("No enabled model provider is configured"));
	}
}
