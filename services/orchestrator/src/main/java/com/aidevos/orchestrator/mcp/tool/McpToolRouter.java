package com.aidevos.orchestrator.mcp.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Unified MCP tool layer router. Every tool invocation goes through the
 * registry lookup, a permission check, the executor and the audit trail
 * (TOOL_STARTED / TOOL_COMPLETED / TOOL_FAILED / TOOL_DENIED). Also exposes
 * the agent -> tool capability binding used by the orchestration layer.
 */
@Component
public class McpToolRouter {

	private final ToolRegistry registry;
	private final AuditService auditService;

	public McpToolRouter(ToolRegistry registry) {
		this(registry, AuditService.noop());
	}

	@Autowired
	public McpToolRouter(ToolRegistry registry, AuditService auditService) {
		this.registry = registry;
		this.auditService = auditService;
	}

	/**
	 * Audits TOOL_REGISTERED for every tool present at startup.
	 */
	@PostConstruct
	public void auditRegisteredTools() {
		for (ToolDefinition tool : registry.listTools()) {
			audit(EventType.TOOL_REGISTERED, tool.toolId(), null, null, "REGISTERED",
				"Tool registered: " + tool.toolId(), Map.of("toolId", tool.toolId(),
					"type", tool.type().name()));
		}
	}

	/**
	 * Routes one tool request: lookup, permission check, execution, audit.
	 */
	public ToolExecutionResult route(ToolExecutionRequest request) {
		long started = System.currentTimeMillis();
		if (request == null || request.toolId() == null) {
			return failure(null, null, null, "Tool id is required", started);
		}
		ToolDefinition tool = registry.getTool(request.toolId());
		McpToolExecutor executor = registry.getExecutor(request.toolId());
		if (tool == null || executor == null) {
			return failure(request.toolId(), request.agentType(), request.taskId(),
				"Tool not found: " + request.toolId(), started);
		}
		ToolPermission requested = requestedPermission(request);
		if (requested != null && !tool.permits(requested)) {
			audit(EventType.TOOL_DENIED, request.toolId(), request.agentType(),
				request.taskId(), "DENIED", "Permission denied: " + requested.name()
					+ " for tool " + request.toolId(), toolMetadata(request, 0));
			return failure(request.toolId(), request.agentType(), request.taskId(),
				"Permission denied: tool " + request.toolId() + " does not grant "
					+ requested.name(), started);
		}
		if (requested == null && dangerousOnly(tool)) {
			audit(EventType.TOOL_DENIED, request.toolId(), request.agentType(),
				request.taskId(), "DENIED", "Dangerous tool requires explicit permission",
				toolMetadata(request, 0));
			return failure(request.toolId(), request.agentType(), request.taskId(),
				"Dangerous tool " + request.toolId() + " requires an explicit DANGEROUS permission",
				started);
		}
		audit(EventType.TOOL_STARTED, request.toolId(), request.agentType(),
			request.taskId(), "STARTED", "Tool started: " + request.toolId(),
			toolMetadata(request, 0));
		try {
			ToolExecutionResult result = executor.execute(request);
			long duration = System.currentTimeMillis() - started;
			ToolExecutionResult timed = result == null
				? ToolExecutionResult.failure("Tool returned no result", Map.of())
				: result.withDuration(duration);
			audit(timed.success() ? EventType.TOOL_COMPLETED : EventType.TOOL_FAILED,
				request.toolId(), request.agentType(), request.taskId(),
				timed.success() ? "COMPLETED" : "FAILED",
				timed.success() ? "Tool completed: " + request.toolId()
					: "Tool failed: " + request.toolId(),
				toolMetadata(request, duration));
			return timed;
		}
		catch (RuntimeException exception) {
			long duration = System.currentTimeMillis() - started;
			audit(EventType.TOOL_FAILED, request.toolId(), request.agentType(),
				request.taskId(), "FAILED", "Tool failed: " + exception.getMessage(),
				toolMetadata(request, duration));
			return failure(request.toolId(), request.agentType(), request.taskId(),
				exception.getMessage(), started);
		}
	}

	/**
	 * Agent -> tool capability binding: CODEX and REPAIR_AGENT get git +
	 * filesystem, OPENCLAW gets browser, TEST_AGENT gets terminal.
	 */
	public List<ToolDefinition> toolsFor(AgentType agentType) {
		List<String> ids = switch (agentType) {
			case CODEX, REPAIR_AGENT -> List.of("git", "filesystem");
			case OPENCLAW -> List.of("browser");
			case TEST_AGENT -> List.of("terminal");
			default -> List.of();
		};
		return ids.stream().map(registry::getTool).filter(Objects::nonNull).toList();
	}

	private boolean dangerousOnly(ToolDefinition tool) {
		return tool.permission().contains(ToolPermission.DANGEROUS)
			&& tool.permission().stream().noneMatch(
				permission -> permission != ToolPermission.DANGEROUS);
	}

	private ToolPermission requestedPermission(ToolExecutionRequest request) {
		Object value = request.parameters().get("permission");
		if (value == null) {
			return null;
		}
		try {
			return ToolPermission.valueOf(String.valueOf(value).toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private Map<String, Object> toolMetadata(ToolExecutionRequest request, long duration) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("toolId", request.toolId());
		if (request.agentType() != null) {
			metadata.put("agentType", request.agentType().name());
		}
		if (request.taskId() != null) {
			metadata.put("taskId", request.taskId());
		}
		metadata.put("duration", duration);
		return metadata;
	}

	private ToolExecutionResult failure(String toolId, AgentType agentType, String taskId,
			String error, long started) {
		return ToolExecutionResult.failure(error,
			Map.of("toolId", String.valueOf(toolId),
				"agentType", agentType == null ? "" : agentType.name(),
				"taskId", taskId == null ? "" : taskId,
				"duration", System.currentTimeMillis() - started));
	}

	private void audit(EventType type, String toolId, AgentType agentType, String taskId,
			String status, String summary, Map<String, Object> metadata) {
		auditService.toolExecutionEvent(type, toolId,
			agentType == null ? null : agentType.name(), taskId, status, summary, metadata);
	}
}
