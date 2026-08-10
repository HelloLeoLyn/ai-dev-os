package com.aidevos.orchestrator.mcp.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * In-memory tool registry. Every Spring-managed McpToolExecutor is
 * registered at construction, pre-registering filesystem, git, browser,
 * docker and terminal tools with their default permissions.
 */
@Component
public class InMemoryToolRegistry implements ToolRegistry {

	private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();
	private final Map<String, McpToolExecutor> executors = new LinkedHashMap<>();

	public InMemoryToolRegistry(List<McpToolExecutor> executors) {
		if (executors != null) {
			for (McpToolExecutor executor : executors) {
				if (executor != null && executor.definition() != null) {
					register(executor.definition(), executor);
				}
			}
		}
	}

	@Override
	public void register(ToolDefinition definition, McpToolExecutor executor) {
		if (definition == null || definition.toolId() == null || definition.toolId().isBlank()) {
			throw new IllegalArgumentException("Tool id is required");
		}
		if (tools.containsKey(definition.toolId())) {
			throw new IllegalStateException("Duplicate tool: " + definition.toolId());
		}
		tools.put(definition.toolId(), definition);
		executors.put(definition.toolId(), executor);
	}

	@Override
	public ToolDefinition getTool(String toolId) {
		return tools.get(toolId);
	}

	@Override
	public McpToolExecutor getExecutor(String toolId) {
		return executors.get(toolId);
	}

	@Override
	public List<ToolDefinition> listTools() {
		return List.copyOf(tools.values());
	}

	@Override
	public List<ToolDefinition> findByType(ToolType type) {
		if (type == null) {
			return List.of();
		}
		List<ToolDefinition> matches = new ArrayList<>();
		for (ToolDefinition definition : tools.values()) {
			if (definition.type() == type) {
				matches.add(definition);
			}
		}
		return List.copyOf(matches);
	}
}
