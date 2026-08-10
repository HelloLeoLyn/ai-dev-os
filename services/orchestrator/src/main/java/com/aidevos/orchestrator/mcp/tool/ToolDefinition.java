package com.aidevos.orchestrator.mcp.tool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Static description of one tool in the registry: identity, category,
 * documentation, an input schema and the granted permissions.
 */
public record ToolDefinition(
		String toolId,
		String name,
		ToolType type,
		String description,
		Map<String, Object> inputSchema,
		Set<ToolPermission> permission) {

	public ToolDefinition {
		if (toolId == null || toolId.isBlank()) {
			throw new IllegalArgumentException("Tool id is required");
		}
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Tool name is required");
		}
		if (type == null) {
			throw new IllegalArgumentException("Tool type is required");
		}
		inputSchema = inputSchema == null ? Map.of()
			: Map.copyOf(new LinkedHashMap<>(inputSchema));
		permission = permission == null || permission.isEmpty()
			? Set.of(ToolPermission.READ) : Set.copyOf(permission);
	}

	public boolean permits(ToolPermission requested) {
		return requested == null || permission.contains(requested);
	}
}
