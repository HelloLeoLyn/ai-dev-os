package com.aidevos.orchestrator.tool;

import java.util.LinkedHashMap;
import java.util.Map;

public record ToolDefinition(String providerId, String name, String description,
		Map<String, Object> inputSchema, ToolAccess access) {

	public ToolDefinition {
		if (providerId == null || providerId.isBlank()) {
			throw new IllegalArgumentException("Tool providerId is required");
		}
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Tool name is required");
		}
		inputSchema = inputSchema == null ? Map.of()
			: Map.copyOf(new LinkedHashMap<>(inputSchema));
		access = access == null ? ToolAccess.READ_ONLY : access;
	}
}
