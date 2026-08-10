package com.aidevos.orchestrator.mcp.tool;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Terminal tool placeholder marked DANGEROUS. Real shell execution is
 * deferred to the MCP server phase and is never run from this layer.
 */
@Component
public class TerminalToolExecutor implements McpToolExecutor {

	@Override
	public ToolDefinition definition() {
		return new ToolDefinition("terminal", "Terminal", ToolType.TERMINAL,
			"Terminal command capability (dangerous, disabled in this phase)",
			Map.of("command", "String"), Set.of(ToolPermission.DANGEROUS));
	}

	@Override
	public ToolExecutionResult execute(ToolExecutionRequest request) {
		return ToolExecutionResult.failure("Terminal execution is disabled in this phase",
			Map.of("wired", false));
	}
}
