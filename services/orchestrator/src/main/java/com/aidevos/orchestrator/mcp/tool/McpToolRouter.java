package com.aidevos.orchestrator.mcp.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.observability.ExecutionTraceService;
import com.aidevos.orchestrator.observability.TraceRecord;
import com.aidevos.orchestrator.security.ApprovalService;
import com.aidevos.orchestrator.security.SecurityPermission;
import com.aidevos.orchestrator.security.SecurityPolicy;
import com.aidevos.orchestrator.security.SecurityPolicyRegistry;
import com.aidevos.orchestrator.security.sandbox.SandboxContext;
import com.aidevos.orchestrator.security.sandbox.SandboxManager;
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
	private final SecurityPolicyRegistry securityPolicyRegistry;
	private final SandboxManager sandboxManager;
	private final ApprovalService approvalService;
	private final ExecutionTraceService traceService;

	public McpToolRouter(ToolRegistry registry) {
		this(registry, AuditService.noop());
	}

	public McpToolRouter(ToolRegistry registry, AuditService auditService) {
		this(registry, auditService, null, null, null, null);
	}

	public McpToolRouter(ToolRegistry registry, AuditService auditService,
			SecurityPolicyRegistry securityPolicyRegistry, SandboxManager sandboxManager,
			ApprovalService approvalService) {
		this(registry, auditService, securityPolicyRegistry, sandboxManager, approvalService,
			null);
	}

	@Autowired
	public McpToolRouter(ToolRegistry registry, AuditService auditService,
			SecurityPolicyRegistry securityPolicyRegistry, SandboxManager sandboxManager,
			ApprovalService approvalService, ExecutionTraceService traceService) {
		this.registry = registry;
		this.auditService = auditService;
		this.securityPolicyRegistry = securityPolicyRegistry;
		this.sandboxManager = sandboxManager;
		this.approvalService = approvalService;
		this.traceService = traceService;
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
		SecurityDenial denial = securityCheck(request, tool);
		if (denial != null) {
			audit(EventType.TOOL_DENIED, request.toolId(), request.agentType(),
				request.taskId(), "DENIED", denial.message(), toolMetadata(request, 0));
			return failure(request.toolId(), request.agentType(), request.taskId(),
				denial.message(), started);
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
		String traceId = startToolTrace(request);
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
			if (timed.success()) {
				completeToolTrace(traceId);
			}
			else {
				failToolTrace(traceId, timed.error());
			}
			return timed;
		}
		catch (RuntimeException exception) {
			long duration = System.currentTimeMillis() - started;
			audit(EventType.TOOL_FAILED, request.toolId(), request.agentType(),
				request.taskId(), "FAILED", "Tool failed: " + exception.getMessage(),
				toolMetadata(request, duration));
			failToolTrace(traceId, exception.getMessage());
			return failure(request.toolId(), request.agentType(), request.taskId(),
				exception.getMessage(), started);
		}
	}

	private String startToolTrace(ToolExecutionRequest request) {
		if (traceService == null) {
			return null;
		}
		TraceRecord trace = traceService.startTool(request.taskId(),
			stringParameter(request, "projectId"), request.toolId(),
			request.agentType() == null ? null : request.agentType().name());
		return trace == null ? null : trace.getTraceId();
	}

	private void completeToolTrace(String traceId) {
		if (traceId != null) {
			traceService.completeNode(traceId);
		}
	}

	private void failToolTrace(String traceId, String error) {
		if (traceId != null) {
			traceService.failNode(traceId, error);
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

	/**
	 * Security & sandbox gate: resolves the task sandbox, evaluates the agent
	 * security policy for the tool's required permission and checks the
	 * sandbox scope. Returns null when the request is allowed, otherwise the
	 * denial message. No-op when the security layer is not wired.
	 */
	private SecurityDenial securityCheck(ToolExecutionRequest request,
			ToolDefinition tool) {
		if (securityPolicyRegistry == null || sandboxManager == null
			|| request.agentType() == null) {
			return null;
		}
		SecurityPermission required = requiredPermission(tool);
		SecurityPolicy policy = securityPolicyRegistry.getPolicy(request.agentType());
		if (policy == null) {
			return new SecurityDenial("No security policy for agent: "
				+ request.agentType());
		}
		SandboxContext sandbox = sandboxManager.getSandbox(request.taskId())
			.orElseGet(() -> sandboxManager.createSandbox(request.taskId(),
				stringParameter(request, "workspaceId"), request.agentType(),
				toolsFor(request.agentType()), policy.allowedPermissions()));
		auditService.securityEvent(EventType.SECURITY_CHECK, request.taskId(),
			request.agentType().name(), required == null ? null : required.name(),
			"CHECK", "Security check for tool " + request.toolId(),
			Map.of("toolId", request.toolId(), "permission",
				required == null ? "" : required.name(), "sandboxId", sandbox.sandboxId()));
		if (!sandbox.allowsTool(request.toolId())) {
			return new SecurityDenial("Tool " + request.toolId()
				+ " is outside the task sandbox scope");
		}
		if (!policy.allows(required)) {
			auditService.securityEvent(EventType.PERMISSION_DENIED, request.taskId(),
				request.agentType().name(), required == null ? null : required.name(),
				"DENIED", "Permission denied for agent " + request.agentType(),
				Map.of("toolId", request.toolId(), "permission",
					required == null ? "" : required.name()));
			return new SecurityDenial("Agent " + request.agentType() + " is not allowed "
				+ (required == null ? "this tool" : required.name()) + " for tool "
				+ request.toolId());
		}
		if (policy.requiresApproval(required)
			&& (approvalService == null
				|| !approvalService.isApproved(request.taskId(), required))) {
			auditService.securityEvent(EventType.PERMISSION_DENIED, request.taskId(),
				request.agentType().name(), required == null ? null : required.name(),
				"APPROVAL_REQUIRED", "Permission requires human approval",
				Map.of("toolId", request.toolId(), "permission",
					required == null ? "" : required.name()));
			return new SecurityDenial("Permission " + (required == null ? "" : required.name())
				+ " requires a human approval for task " + request.taskId());
		}
		auditService.securityEvent(EventType.PERMISSION_GRANTED, request.taskId(),
			request.agentType().name(), required == null ? null : required.name(),
			"GRANTED", "Permission granted for agent " + request.agentType(),
			Map.of("toolId", request.toolId(), "permission",
				required == null ? "" : required.name(), "sandboxId", sandbox.sandboxId()));
		return null;
	}

	private SecurityPermission requiredPermission(ToolDefinition tool) {
		if (tool == null || tool.type() == null) {
			return null;
		}
		return switch (tool.type()) {
			case FILESYSTEM -> SecurityPermission.READ_FILE;
			case GIT -> SecurityPermission.GIT_WRITE;
			case BROWSER -> SecurityPermission.BROWSER_EXECUTE;
			case DOCKER, TERMINAL -> SecurityPermission.EXECUTE_COMMAND;
			case DATABASE -> SecurityPermission.NETWORK_ACCESS;
		};
	}

	private String stringParameter(ToolExecutionRequest request, String key) {
		Object value = request.parameters().get(key);
		return value == null ? null : String.valueOf(value);
	}

	private record SecurityDenial(String message) {
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
