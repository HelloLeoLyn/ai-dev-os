package com.aidevos.orchestrator.security;

import java.util.Set;

import com.aidevos.orchestrator.agent.AgentType;

/**
 * Security policy for one agent type: which permissions are allowed, which
 * are explicitly denied and which require a human approval before the MCP
 * router may execute the corresponding tool.
 */
public record SecurityPolicy(
		String policyId,
		AgentType agentType,
		Set<SecurityPermission> allowedPermissions,
		Set<SecurityPermission> deniedPermissions,
		Set<SecurityPermission> requireApprovalPermissions) {

	public SecurityPolicy {
		if (policyId == null || policyId.isBlank()) {
			throw new IllegalArgumentException("Policy id is required");
		}
		if (agentType == null) {
			throw new IllegalArgumentException("Agent type is required");
		}
		allowedPermissions = allowedPermissions == null ? Set.of()
			: Set.copyOf(allowedPermissions);
		deniedPermissions = deniedPermissions == null ? Set.of()
			: Set.copyOf(deniedPermissions);
		requireApprovalPermissions = requireApprovalPermissions == null ? Set.of()
			: Set.copyOf(requireApprovalPermissions);
	}

	public boolean allows(SecurityPermission permission) {
		if (permission == null) {
			return true;
		}
		return allowedPermissions.contains(permission)
			&& !deniedPermissions.contains(permission);
	}

	public boolean requiresApproval(SecurityPermission permission) {
		return permission != null && requireApprovalPermissions.contains(permission);
	}
}
