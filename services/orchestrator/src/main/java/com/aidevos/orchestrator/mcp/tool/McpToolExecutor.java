package com.aidevos.orchestrator.mcp.tool;

/**
 * Executor contract for one tool: exposes its static definition and runs a
 * request. Implementations reuse the existing Git / Browser / Filesystem
 * capabilities instead of duplicating business logic.
 */
public interface McpToolExecutor {

	ToolDefinition definition();

	ToolExecutionResult execute(ToolExecutionRequest request);
}
