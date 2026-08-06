package com.aidevos.orchestrator.mcpplugin;

import com.aidevos.orchestrator.tool.ToolAccess;

/**
 * A tool exposed by an MCP plugin. Read-only access is allowed by the existing
 * tool policy; workspace-write access requires confirmation through the
 * existing approval flow.
 */
public record McpPluginTool(String name, String description, ToolAccess access,
		boolean dangerous) {

	public McpPluginTool {
		access = access == null ? ToolAccess.READ_ONLY : access;
	}
}
