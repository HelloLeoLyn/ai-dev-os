package com.aidevos.orchestrator.mcpplugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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

	public McpPluginRegistryService(McpPluginConfigLoader configLoader) {
		configLoader.loadPlugins().forEach(this::register);
	}

	public McpPlugin register(McpPlugin plugin) {
		if (plugin == null || isBlank(plugin.getPluginId())) {
			throw new IllegalArgumentException("Plugin id is required");
		}
		McpPlugin previous = plugins.putIfAbsent(plugin.getPluginId(), plugin);
		if (previous != null) {
			throw new IllegalArgumentException("Plugin already registered: " + plugin.getPluginId());
		}
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
		plugin.ifPresent(McpPlugin::enable);
		return plugin;
	}

	public Optional<McpPlugin> disable(String pluginId) {
		Optional<McpPlugin> plugin = getPlugin(pluginId);
		plugin.ifPresent(McpPlugin::disable);
		return plugin;
	}

	public List<McpPluginTool> getTools(String pluginId) {
		return getPlugin(pluginId).map(McpPlugin::getTools).orElseGet(List::of);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
