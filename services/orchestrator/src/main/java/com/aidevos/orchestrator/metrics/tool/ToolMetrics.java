package com.aidevos.orchestrator.metrics.tool;

/**
 * Aggregated statistics for one MCP tool, derived on demand from the
 * TOOL_STARTED / TOOL_COMPLETED / TOOL_FAILED / TOOL_DENIED audit events.
 */
public record ToolMetrics(
		String toolId,
		long executeCount,
		long successCount,
		long failedCount,
		long deniedCount,
		long averageDurationMillis) {
}
