package com.aidevos.orchestrator.mcp.tool;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Docker tool placeholder for the internal tool abstraction. Real container
 * execution is deferred to the MCP server phase; this executor only validates
 * the request shape and reports the capability state.
 */
@Component
public class DockerToolExecutor implements McpToolExecutor {

	@Override
	public ToolDefinition definition() {
		return new ToolDefinition("docker", "Docker", ToolType.DOCKER,
			"Docker container capability (execution deferred to MCP server phase)",
			Map.of("operation", "String"), Set.of(ToolPermission.EXECUTE));
	}

	@Override
	public ToolExecutionResult execute(ToolExecutionRequest request) {
		return ToolExecutionResult.success("docker:capability-registered",
			Map.of("wired", false, "reason", "MCP server phase"));
	}
}
