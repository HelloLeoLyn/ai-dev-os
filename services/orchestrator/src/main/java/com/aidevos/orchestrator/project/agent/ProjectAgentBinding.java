package com.aidevos.orchestrator.project.agent;

import com.aidevos.orchestrator.agent.AgentType;

/**
 * Agent binding for one project: which agent types are enabled for the
 * project and their priority. Project A and project B can enable different
 * agent sets.
 */
public record ProjectAgentBinding(
		String projectId,
		AgentType agentType,
		boolean enabled,
		int priority) {

	public ProjectAgentBinding {
		if (projectId == null || projectId.isBlank()) {
			throw new IllegalArgumentException("Project id is required");
		}
		if (agentType == null) {
			throw new IllegalArgumentException("Agent type is required");
		}
		priority = priority <= 0 ? 10 : priority;
	}
}
