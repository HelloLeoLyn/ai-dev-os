package com.aidevos.orchestrator.mcp.tool;

import java.util.List;

/**
 * Registry of available MCP tools: registration, lookup by id, listing and
 * category based queries.
 */
public interface ToolRegistry {

	void register(ToolDefinition definition, McpToolExecutor executor);

	ToolDefinition getTool(String toolId);

	McpToolExecutor getExecutor(String toolId);

	List<ToolDefinition> listTools();

	List<ToolDefinition> findByType(ToolType type);
}
