package com.aidevos.orchestrator.execution.tool;

import com.aidevos.orchestrator.execution.FailureClass;

/**
 * Outcome of a deterministic tool invocation: success flag, exit code,
 * captured output/error, elapsed millis and a deterministic failure class.
 */
public record ToolExecutionResult(DeterministicTool tool, boolean success, int exitCode,
		String output, String error, long durationMs, FailureClass failureClass) {
}
