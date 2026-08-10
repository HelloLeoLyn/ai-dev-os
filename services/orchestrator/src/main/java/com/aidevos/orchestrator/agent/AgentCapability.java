package com.aidevos.orchestrator.agent;

import java.util.List;

/**
 * What one agent can do: its role (agentType), display name, description,
 * the task categories it supports and the tools it can use. Used by the
 * AgentRegistry capability matching and the AgentSelector.
 */
public record AgentCapability(
		AgentType agentType,
		String name,
		String description,
		List<String> supportedTasks,
		List<String> availableTools) {

	public AgentCapability {
		supportedTasks = supportedTasks == null ? List.of() : List.copyOf(supportedTasks);
		availableTools = availableTools == null ? List.of() : List.copyOf(availableTools);
	}

	public boolean supports(String taskCategory) {
		if (taskCategory == null || taskCategory.isBlank()) {
			return false;
		}
		if (name != null && name.equalsIgnoreCase(taskCategory)) {
			return true;
		}
		return supportedTasks.stream().anyMatch(task -> task.equalsIgnoreCase(taskCategory));
	}
}
