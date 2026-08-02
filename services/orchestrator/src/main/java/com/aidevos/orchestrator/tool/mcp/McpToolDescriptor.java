package com.aidevos.orchestrator.tool.mcp;

import java.util.Map;

public record McpToolDescriptor(String name, String description, Map<String, Object> inputSchema,
		Map<String, Object> annotations) {
}
