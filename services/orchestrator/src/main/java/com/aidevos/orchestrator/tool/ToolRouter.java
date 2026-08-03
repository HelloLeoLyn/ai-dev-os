package com.aidevos.orchestrator.tool;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.aidevos.orchestrator.tool.policy.ToolPolicy;
import com.aidevos.orchestrator.tool.policy.ToolPolicyDecision;
import com.aidevos.orchestrator.tool.policy.ToolPolicyAction;
import com.aidevos.orchestrator.tool.approval.ToolApprovalDecision;
import com.aidevos.orchestrator.tool.approval.ToolApprovalService;
import com.aidevos.orchestrator.tool.approval.ToolApprovalStore;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ToolRouter {

	private final ToolRegistry registry;
	private final ToolPolicy policy;
	private final ToolApprovalService approvalService;
	private final ExecutorService executor;
	private final AuditService auditService;

	@Autowired
	public ToolRouter(ToolRegistry registry, ToolPolicy policy, ToolApprovalService approvalService,
			AuditService auditService) {
		this(registry, policy, approvalService, Executors.newVirtualThreadPerTaskExecutor(),
			auditService);
	}

	public ToolRouter(ToolRegistry registry, ToolPolicy policy, ToolApprovalService approvalService) {
		this(registry, policy, approvalService, Executors.newVirtualThreadPerTaskExecutor(),
			AuditService.noop());
	}

	public ToolRouter(ToolRegistry registry, ToolPolicy policy) {
		this(registry, policy, new ToolApprovalService(new ToolApprovalStore(),
			new tools.jackson.databind.ObjectMapper()), Executors.newVirtualThreadPerTaskExecutor(),
			AuditService.noop());
	}

	ToolRouter(ToolRegistry registry, ToolPolicy policy, ToolApprovalService approvalService,
			ExecutorService executor) {
		this(registry, policy, approvalService, executor, AuditService.noop());
	}

	ToolRouter(ToolRegistry registry, ToolPolicy policy, ToolApprovalService approvalService,
			ExecutorService executor, AuditService auditService) {
		this.registry = registry;
		this.policy = policy;
		this.approvalService = approvalService;
		this.executor = executor;
		this.auditService = auditService;
	}

	public ToolResult invoke(ToolInvocation invocation) {
		auditService.toolEvent(EventType.TOOL_INVOCATION_CREATED, invocation, null, "CREATED", null);
		ToolDefinition definition = registry.getTool(invocation.providerId(), invocation.toolName());
		ToolProvider provider = registry.getProvider(invocation.providerId());
		if (definition == null || provider == null) {
			return auditedFailure(invocation, null, "TOOL_NOT_FOUND", "Tool not found: "
				+ invocation.providerId() + "/" + invocation.toolName());
		}
		ToolPolicyDecision decision = policy.evaluate(definition, invocation);
		if (decision.action() == ToolPolicyAction.DENY) {
			return auditedFailure(invocation, null, "TOOL_DENIED", decision.reason());
		}
		String approvalId = null;
		if (decision.action() == ToolPolicyAction.REQUIRE_APPROVAL) {
			ToolApprovalDecision approval = approvalService.authorize(invocation, definition,
				decision.reason());
			approvalId = approval.approvalId();
			if (approval.approvalRequired()) {
				auditService.toolEvent(EventType.TOOL_APPROVAL_REQUIRED, invocation, approvalId,
					"WAITING_APPROVAL", "TOOL_APPROVAL_REQUIRED");
				return waitingApproval(invocation, approvalId);
			}
		}

		String invocationApprovalId = approvalId;
		Future<ToolResult> future = executor.submit(() -> {
			auditService.toolEvent(EventType.TOOL_STARTED, invocation, invocationApprovalId,
				"RUNNING", null);
			return provider.invoke(invocation);
		});
		try {
			ToolResult result = future.get(invocation.timeout().toMillis(), TimeUnit.MILLISECONDS);
			if (result == null) {
				return auditedFailure(invocation, approvalId, "TOOL_INVALID_RESULT",
					"Tool provider returned no result");
			}
			ToolResult completed = withApproval(result.withInvocation(invocation), approvalId);
			auditService.toolEvent(completed.success() ? EventType.TOOL_COMPLETED : EventType.TOOL_FAILED,
				invocation, approvalId, completed.success() ? "COMPLETED" : "FAILED", completed.code());
			return completed;
		}
		catch (TimeoutException exception) {
			future.cancel(true);
			return auditedFailure(invocation, approvalId, "TOOL_TIMEOUT", "Tool invocation timed out");
		}
		catch (InterruptedException exception) {
			future.cancel(true);
			Thread.currentThread().interrupt();
			return auditedFailure(invocation, approvalId, "TOOL_INTERRUPTED",
				"Tool invocation was interrupted");
		}
		catch (ExecutionException exception) {
			return auditedFailure(invocation, approvalId, "TOOL_PROVIDER_ERROR",
				errorMessage(exception.getCause()));
		}
	}

	private ToolResult auditedFailure(ToolInvocation invocation, String approvalId, String code,
			String message) {
		ToolResult result = withApproval(failure(invocation, code, message), approvalId);
		auditService.toolEvent(EventType.TOOL_FAILED, invocation, approvalId, "FAILED", code);
		return result;
	}

	private ToolResult waitingApproval(ToolInvocation invocation, String approvalId) {
		Map<String, Object> metadata = new java.util.LinkedHashMap<>();
		metadata.put("approvalRequired", true);
		metadata.put("approvalId", approvalId);
		return new ToolResult(invocation.executionId(), invocation.invocationId(), false,
			"TOOL_APPROVAL_REQUIRED", "APPROVAL_REQUIRED", null, java.util.List.of(), metadata);
	}

	private ToolResult withApproval(ToolResult result, String approvalId) {
		if (approvalId == null) {
			return result;
		}
		Map<String, Object> metadata = new java.util.LinkedHashMap<>(result.metadata());
		metadata.put("approvalId", approvalId);
		return new ToolResult(result.executionId(), result.invocationId(), result.success(),
			result.code(), result.message(), result.output(), result.content(), metadata);
	}

	private ToolResult failure(ToolInvocation invocation, String code, String message) {
		return ToolResult.failure(code, message).withInvocation(invocation);
	}

	private String errorMessage(Throwable throwable) {
		if (throwable == null) {
			return "Tool provider failed";
		}
		String message = throwable.getMessage();
		return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
	}

	@PreDestroy
	public void close() {
		executor.shutdownNow();
	}
}
