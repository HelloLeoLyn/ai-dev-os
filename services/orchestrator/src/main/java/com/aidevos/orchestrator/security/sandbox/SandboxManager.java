package com.aidevos.orchestrator.security.sandbox;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.mcp.tool.ToolDefinition;
import com.aidevos.orchestrator.security.SecurityPermission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Creates, isolates and destroys per-task sandboxes. Creating a sandbox for
 * the same task is idempotent: one task has exactly one SandboxContext.
 */
@Component
public class SandboxManager {

	private final Map<String, SandboxContext> sandboxes = new ConcurrentHashMap<>();
	private final AuditService auditService;

	public SandboxManager() {
		this(AuditService.noop());
	}

	@Autowired
	public SandboxManager(AuditService auditService) {
		this.auditService = auditService;
	}

	/**
	 * Creates (or returns the existing) sandbox for a task. The allowed tools
	 * default to the agent binding when not provided; an empty set means the
	 * sandbox does not restrict tools further than the security policy.
	 */
	public SandboxContext createSandbox(String taskId, String workspaceId, AgentType agentType,
			List<ToolDefinition> allowedTools, Set<SecurityPermission> permissions) {
		SandboxContext existing = sandboxes.get(taskId);
		if (existing != null) {
			return existing;
		}
		Set<String> toolIds = allowedTools == null ? Set.of()
			: allowedTools.stream().map(ToolDefinition::toolId).collect(java.util.stream.Collectors.toSet());
		SandboxContext context = new SandboxContext("sandbox-" + UUID.randomUUID(), taskId,
			workspaceId, agentType, toolIds,
			permissions == null ? Set.of() : Set.copyOf(permissions), Instant.now());
		sandboxes.put(taskId, context);
		auditService.sandboxEvent(EventType.SANDBOX_CREATED, context.sandboxId(), taskId,
			agentType == null ? null : agentType.name(), "Sandbox created",
			Map.of("sandboxId", context.sandboxId(), "taskId", taskId,
				"workspaceId", workspaceId == null ? "" : workspaceId));
		return context;
	}

	public Optional<SandboxContext> getSandbox(String taskId) {
		return Optional.ofNullable(taskId == null ? null : sandboxes.get(taskId));
	}

	public Optional<SandboxContext> destroySandbox(String taskId) {
		SandboxContext removed = taskId == null ? null : sandboxes.remove(taskId);
		if (removed != null) {
			auditService.sandboxEvent(EventType.SANDBOX_DESTROYED, removed.sandboxId(), taskId,
				removed.agentType() == null ? null : removed.agentType().name(), "Sandbox destroyed",
				Map.of("sandboxId", removed.sandboxId(), "taskId", taskId));
		}
		return Optional.ofNullable(removed);
	}

	public List<SandboxContext> listSandboxes() {
		return List.copyOf(sandboxes.values());
	}
}
