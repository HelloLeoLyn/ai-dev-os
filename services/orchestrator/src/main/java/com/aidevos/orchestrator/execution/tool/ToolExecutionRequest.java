package com.aidevos.orchestrator.execution.tool;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * A deterministic tool invocation: which tool, its arguments, the working
 * directory and an optional timeout. Never routed through an LLM.
 */
public record ToolExecutionRequest(DeterministicTool tool, List<String> arguments,
		String workdir, Duration timeout, Map<String, String> environment) {

	public ToolExecutionRequest {
		arguments = arguments == null ? List.of() : List.copyOf(arguments);
		environment = environment == null ? Map.of() : Map.copyOf(environment);
	}

	public static ToolExecutionRequest of(DeterministicTool tool, List<String> arguments,
			String workdir) {
		return new ToolExecutionRequest(tool, arguments, workdir, null, Map.of());
	}

	public static ToolExecutionRequest of(DeterministicTool tool, List<String> arguments,
			String workdir, Duration timeout) {
		return new ToolExecutionRequest(tool, arguments, workdir, timeout, Map.of());
	}
}
