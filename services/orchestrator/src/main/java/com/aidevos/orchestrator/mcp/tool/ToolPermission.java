package com.aidevos.orchestrator.mcp.tool;

/**
 * Permission level of a registered tool. The McpToolRouter denies requests
 * whose requested permission is not granted by the tool definition.
 */
public enum ToolPermission {
	READ,
	WRITE,
	EXECUTE,
	DANGEROUS
}
