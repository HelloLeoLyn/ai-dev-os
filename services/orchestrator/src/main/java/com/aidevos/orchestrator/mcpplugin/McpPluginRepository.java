package com.aidevos.orchestrator.mcpplugin;

import java.util.List;

/**
 * Persistence boundary for the MCP plugin registry state (enabled and
 * permission level). Plugin definitions come from mcp-plugins.yaml; the
 * repository keeps the runtime state across restarts.
 */
public interface McpPluginRepository {

	void save(McpPlugin plugin);

	McpPlugin get(String pluginId);

	List<McpPlugin> list();

	boolean delete(String pluginId);
}
