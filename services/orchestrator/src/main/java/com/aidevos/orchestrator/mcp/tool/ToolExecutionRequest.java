package com.aidevos.orchestrator.mcp.tool;

import java.util.Map;

import com.aidevos.orchestrator.agent.AgentType;

/**
 * A single tool invocation from an agent: which tool, which agent, which
 * task and the operation parameters.
 */
public record ToolExecutionRequest(
		String toolId,
		AgentType agentType,
		String taskId,
		Map<String, Object> parameters) {

	public ToolExecutionRequest {
		if (toolId == null || toolId.isBlank()) {
			throw new IllegalArgumentException("Tool id is required");
		}
		parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
	}
}
