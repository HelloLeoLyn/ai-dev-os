package com.aidevos.orchestrator.mcpplugin;

import java.util.List;

/**
 * An MCP plugin: a managed bundle of MCP tools with a permission policy.
 * Read-only by default; tools flagged dangerous (workspace-write access)
 * require confirmation through the existing tool approval flow when invoked.
 */
public class McpPlugin {

	public static final String PERMISSION_READ_ONLY = "read-only";

	public static final String PERMISSION_WORKSPACE_WRITE = "workspace-write";

	private final String pluginId;
	private final String name;
	private final String type;
	private final String description;
	private final String permissionLevel;
	private final List<McpPluginTool> tools;
	private volatile boolean enabled;

	public McpPlugin(String pluginId, String name, String type, String description,
			String permissionLevel, boolean enabled, List<McpPluginTool> tools) {
		this.pluginId = pluginId;
		this.name = name;
		this.type = type;
		this.description = description;
		this.permissionLevel = permissionLevel == null || permissionLevel.isBlank()
			? PERMISSION_READ_ONLY : permissionLevel;
		this.enabled = enabled;
		this.tools = tools == null ? List.of() : List.copyOf(tools);
	}

	public synchronized void enable() {
		this.enabled = true;
	}

	public synchronized void disable() {
		this.enabled = false;
	}

	public String getPluginId() {
		return pluginId;
	}

	public String getName() {
		return name;
	}

	public String getType() {
		return type;
	}

	public String getDescription() {
		return description;
	}

	public String getPermissionLevel() {
		return permissionLevel;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public List<McpPluginTool> getTools() {
		return tools;
	}
}
