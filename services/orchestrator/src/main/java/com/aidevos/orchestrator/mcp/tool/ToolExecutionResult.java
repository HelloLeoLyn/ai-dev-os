package com.aidevos.orchestrator.mcp.tool;

import java.util.Map;

/**
 * Structured outcome of a tool execution: success flag, output, error and
 * the measured duration in milliseconds plus arbitrary metadata.
 */
public record ToolExecutionResult(
		boolean success,
		String output,
		String error,
		long duration,
		Map<String, Object> metadata) {

	public ToolExecutionResult {
		metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
	}

	public static ToolExecutionResult success(String output, Map<String, Object> metadata) {
		return new ToolExecutionResult(true, output, null, 0, metadata);
	}

	public static ToolExecutionResult failure(String error, Map<String, Object> metadata) {
		return new ToolExecutionResult(false, null, error, 0, metadata);
	}

	public ToolExecutionResult withDuration(long millis) {
		return new ToolExecutionResult(success, output, error, millis, metadata);
	}
}
