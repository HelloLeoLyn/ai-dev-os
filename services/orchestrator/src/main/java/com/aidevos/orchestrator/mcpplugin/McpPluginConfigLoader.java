package com.aidevos.orchestrator.mcpplugin;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.tool.ToolAccess;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads mcp-plugins.yaml into McpPlugin definitions. Mirrors AgentConfigLoader;
 * read-only configuration used by the plugin registry.
 */
@Component
public class McpPluginConfigLoader {

	private static final String CONFIG_FILE = "mcp-plugins.yaml";

	public List<McpPlugin> loadPlugins() {
		try (InputStream inputStream = getClass().getClassLoader()
				.getResourceAsStream(CONFIG_FILE)) {
			if (inputStream == null) {
				throw new IllegalStateException("Configuration file not found: " + CONFIG_FILE);
			}
			Map<String, Object> config = new Yaml().load(inputStream);
			return toPlugins(config.get("plugins"));
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to read configuration file: " + CONFIG_FILE,
				exception);
		}
	}

	List<McpPlugin> toPlugins(Object value) {
		if (!(value instanceof List<?> pluginValues)) {
			throw new IllegalStateException("Invalid plugins configuration");
		}
		List<McpPlugin> plugins = new ArrayList<>();
		Set<String> pluginIds = new HashSet<>();
		for (Object pluginValue : pluginValues) {
			if (!(pluginValue instanceof Map<?, ?> map)) {
				throw new IllegalStateException("Invalid plugin definition");
			}
			McpPlugin plugin = toPlugin(map);
			if (isBlank(plugin.getPluginId())) {
				throw new IllegalStateException("pluginId is required");
			}
			if (!pluginIds.add(plugin.getPluginId())) {
				throw new IllegalStateException("Duplicate pluginId: " + plugin.getPluginId());
			}
			plugins.add(plugin);
		}
		return plugins;
	}

	private McpPlugin toPlugin(Map<?, ?> map) {
		String permissionLevel = string(map, "permissionLevel");
		if (permissionLevel == null || permissionLevel.isBlank()) {
			permissionLevel = McpPlugin.PERMISSION_READ_ONLY;
		}
		boolean enabled = !map.containsKey("enabled") || Boolean.TRUE.equals(map.get("enabled"));
		return new McpPlugin(string(map, "pluginId"), string(map, "name"),
			string(map, "type"), string(map, "description"), permissionLevel, enabled,
			toTools(map.get("tools")));
	}

	private List<McpPluginTool> toTools(Object value) {
		if (!(value instanceof List<?> toolValues)) {
			return List.of();
		}
		List<McpPluginTool> tools = new ArrayList<>();
		for (Object toolValue : toolValues) {
			if (!(toolValue instanceof Map<?, ?> map)) {
				throw new IllegalStateException("Invalid tool definition");
			}
			ToolAccess access = ToolAccess.READ_ONLY;
			if (map.containsKey("access")) {
				try {
					access = ToolAccess.valueOf(string(map, "access"));
				}
				catch (IllegalArgumentException exception) {
					throw new IllegalStateException("Invalid tool access: " + string(map, "access"));
				}
			}
			tools.add(new McpPluginTool(string(map, "name"), string(map, "description"), access,
				Boolean.TRUE.equals(map.get("dangerous"))));
		}
		return tools;
	}

	private String string(Map<?, ?> map, String key) {
		Object value = map.get(key);
		return value == null ? null : String.valueOf(value);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
