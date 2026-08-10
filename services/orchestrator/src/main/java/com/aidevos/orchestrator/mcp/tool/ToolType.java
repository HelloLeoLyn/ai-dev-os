package com.aidevos.orchestrator.mcp.tool;

/**
 * Tool category used by the unified MCP tool layer. Each tool belongs to
 * exactly one category and is bound to agents by capability.
 */
public enum ToolType {
	FILESYSTEM,
	GIT,
	BROWSER,
	DOCKER,
	TERMINAL,
	DATABASE
}
