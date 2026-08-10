package com.aidevos.orchestrator.security.sandbox;

import java.time.Instant;
import java.util.Set;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.security.SecurityPermission;

/**
 * Isolation boundary for one task: the allowed tools and security
 * permissions the agents of that task may use. Every task gets its own
 * sandbox; tools or permissions outside the sandbox are denied.
 */
public record SandboxContext(
		String sandboxId,
		String taskId,
		String workspaceId,
		AgentType agentType,
		Set<String> allowedTools,
		Set<SecurityPermission> permissions,
		Instant createdAt) {

	public SandboxContext {
		if (sandboxId == null || sandboxId.isBlank()) {
			throw new IllegalArgumentException("Sandbox id is required");
		}
		if (taskId == null || taskId.isBlank()) {
			throw new IllegalArgumentException("Task id is required");
		}
		allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
		permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
		createdAt = createdAt == null ? Instant.now() : createdAt;
	}

	public boolean allowsTool(String toolId) {
		return allowedTools.isEmpty() || (toolId != null && allowedTools.contains(toolId));
	}

	public boolean allowsPermission(SecurityPermission permission) {
		return permissions.isEmpty() || (permission != null && permissions.contains(permission));
	}
}
