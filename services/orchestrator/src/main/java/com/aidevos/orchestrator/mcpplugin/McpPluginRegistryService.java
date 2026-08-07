package com.aidevos.orchestrator.mcpplugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * MCP plugin registry: registers plugins (from mcp-plugins.yaml or
 * programmatically), queries plugins and their tools, and toggles the enabled
 * state. Management layer only - the MCP protocol, agent execution flow,
 * Scheduler and Worker are not touched.
 */
@Service
public class McpPluginRegistryService {

	private final Map<String, McpPlugin> plugins = new ConcurrentHashMap<>();
	private final McpPluginRepository repository;

	public McpPluginRegistryService(McpPluginConfigLoader configLoader) {
		this(configLoader, new InMemoryMcpPluginRepository());
	}

	@Autowired
	public McpPluginRegistryService(McpPluginConfigLoader configLoader,
			McpPluginRepository repository) {
		this.repository = repository;
		configLoader.loadPlugins().forEach(plugin -> register(restore(plugin)));
	}

	public McpPlugin register(McpPlugin plugin) {
		if (plugin == null || isBlank(plugin.getPluginId())) {
			throw new IllegalArgumentException("Plugin id is required");
		}
		McpPlugin previous = plugins.putIfAbsent(plugin.getPluginId(), plugin);
		if (previous != null) {
			throw new IllegalArgumentException("Plugin already registered: " + plugin.getPluginId());
		}
		repository.save(plugin);
		return plugin;
	}

	public List<McpPlugin> listPlugins() {
		List<McpPlugin> result = new ArrayList<>(plugins.values());
		result.sort(Comparator.comparing(McpPlugin::getPluginId));
		return result;
	}

	public Optional<McpPlugin> getPlugin(String pluginId) {
		if (isBlank(pluginId)) {
			return Optional.empty();
		}
		return Optional.ofNullable(plugins.get(pluginId));
	}

	public Optional<McpPlugin> enable(String pluginId) {
		Optional<McpPlugin> plugin = getPlugin(pluginId);
		plugin.ifPresent(value -> {
			value.enable();
			repository.save(value);
		});
		return plugin;
	}

	public Optional<McpPlugin> disable(String pluginId) {
		Optional<McpPlugin> plugin = getPlugin(pluginId);
		plugin.ifPresent(value -> {
			value.disable();
			repository.save(value);
		});
		return plugin;
	}

	public List<McpPluginTool> getTools(String pluginId) {
		return getPlugin(pluginId).map(McpPlugin::getTools).orElseGet(List::of);
	}

	private McpPlugin restore(McpPlugin plugin) {
		McpPlugin persisted = repository.get(plugin.getPluginId());
		if (persisted == null) {
			return plugin;
		}
		return new McpPlugin(plugin.getPluginId(), plugin.getName(), plugin.getType(),
			plugin.getDescription(), persisted.getPermissionLevel(), persisted.isEnabled(),
			plugin.getTools());
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
