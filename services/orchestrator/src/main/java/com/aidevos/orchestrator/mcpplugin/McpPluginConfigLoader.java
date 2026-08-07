package com.aidevos.orchestrator.mcpplugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.common.config.YamlConfigSupport;
import com.aidevos.orchestrator.tool.ToolAccess;
import org.springframework.stereotype.Component;

/**
 * Loads mcp-plugins.yaml into McpPlugin definitions. YAML reading, conversions
 * and validation helpers come from YamlConfigSupport; read-only configuration
 * used by the plugin registry.
 */
@Component
public class McpPluginConfigLoader {

	private static final String CONFIG_FILE = "mcp-plugins.yaml";

	public List<McpPlugin> loadPlugins() {
		return toPlugins(YamlConfigSupport.load(CONFIG_FILE).get("plugins"));
	}

	List<McpPlugin> toPlugins(Object value) {
		List<McpPlugin> plugins = new ArrayList<>();
		Set<String> pluginIds = YamlConfigSupport.newIdentitySet();
		for (Map<String, Object> map : YamlConfigSupport.asList(value, "plugins", "plugin")) {
			McpPlugin plugin = toPlugin(map);
			YamlConfigSupport.require(plugin.getPluginId(), "pluginId");
			YamlConfigSupport.requireUnique(pluginIds, plugin.getPluginId(), "pluginId");
			plugins.add(plugin);
		}
		return plugins;
	}

	private McpPlugin toPlugin(Map<String, Object> map) {
		String permissionLevel = YamlConfigSupport.string(map, "permissionLevel");
		if (YamlConfigSupport.isBlank(permissionLevel)) {
			permissionLevel = McpPlugin.PERMISSION_READ_ONLY;
		}
		boolean enabled = YamlConfigSupport.bool(map, "enabled", true);
		return new McpPlugin(YamlConfigSupport.string(map, "pluginId"),
			YamlConfigSupport.string(map, "name"), YamlConfigSupport.string(map, "type"),
			YamlConfigSupport.string(map, "description"), permissionLevel, enabled,
			toTools(map.get("tools")));
	}

	private List<McpPluginTool> toTools(Object value) {
		if (!(value instanceof List<?>)) {
			return List.of();
		}
		List<McpPluginTool> tools = new ArrayList<>();
		for (Map<String, Object> map : YamlConfigSupport.asList(value, "tools", "tool")) {
			ToolAccess access = ToolAccess.READ_ONLY;
			if (map.containsKey("access")) {
				try {
					access = ToolAccess.valueOf(YamlConfigSupport.string(map, "access"));
				}
				catch (IllegalArgumentException exception) {
					throw new IllegalStateException("Invalid tool access: "
						+ YamlConfigSupport.string(map, "access"));
				}
			}
			tools.add(new McpPluginTool(YamlConfigSupport.string(map, "name"),
				YamlConfigSupport.string(map, "description"), access,
				Boolean.TRUE.equals(map.get("dangerous"))));
		}
		return tools;
	}
}
