package com.aidevos.orchestrator.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class ToolRegistry {

	private final Map<String, ToolProvider> providers = new LinkedHashMap<>();
	private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();

	public ToolRegistry(List<ToolProvider> providers) {
		for (ToolProvider provider : providers) {
			register(provider);
		}
	}

	public ToolProvider getProvider(String providerId) {
		return providers.get(providerId);
	}

	public ToolDefinition getTool(String providerId, String toolName) {
		return tools.get(key(providerId, toolName));
	}

	public List<ToolDefinition> getTools() {
		return List.copyOf(tools.values());
	}

	private void register(ToolProvider provider) {
		if (provider == null || provider.getId() == null || provider.getId().isBlank()) {
			throw new IllegalStateException("Tool provider id is required");
		}
		if (providers.putIfAbsent(provider.getId(), provider) != null) {
			throw new IllegalStateException("Duplicate tool provider: " + provider.getId());
		}
		List<ToolDefinition> definitions = provider.getTools();
		if (definitions == null) {
			throw new IllegalStateException("Tool definitions are required: " + provider.getId());
		}
		for (ToolDefinition definition : definitions) {
			if (!provider.getId().equals(definition.providerId())) {
				throw new IllegalStateException("Tool provider mismatch: " + definition.name());
			}
			if (tools.putIfAbsent(key(definition.providerId(), definition.name()), definition) != null) {
				throw new IllegalStateException("Duplicate tool: " + definition.providerId()
					+ "/" + definition.name());
			}
		}
	}

	private String key(String providerId, String toolName) {
		return providerId + "\u0000" + toolName;
	}
}
