package com.aidevos.orchestrator.mcpplugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryMcpPluginRepository implements McpPluginRepository {

	private final Map<String, McpPlugin> plugins = new LinkedHashMap<>();

	@Override
	public synchronized void save(McpPlugin plugin) {
		plugins.put(plugin.getPluginId(), plugin);
	}

	@Override
	public synchronized McpPlugin get(String pluginId) {
		return plugins.get(pluginId);
	}

	@Override
	public synchronized List<McpPlugin> list() {
		return new ArrayList<>(plugins.values());
	}

	@Override
	public synchronized boolean delete(String pluginId) {
		return plugins.remove(pluginId) != null;
	}
}
