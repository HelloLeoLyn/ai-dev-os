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
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ToolRouter {

	private final ToolRegistry registry;
	private final ToolPolicy policy;
	private final ToolApprovalService approvalService;
	private final ExecutorService executor;

	@Autowired
	public ToolRouter(ToolRegistry registry, ToolPolicy policy, ToolApprovalService approvalService) {
		this(registry, policy, approvalService, Executors.newVirtualThreadPerTaskExecutor());
	}

	public ToolRouter(ToolRegistry registry, ToolPolicy policy) {
		this(registry, policy, new ToolApprovalService(new ToolApprovalStore(),
			new tools.jackson.databind.ObjectMapper()), Executors.newVirtualThreadPerTaskExecutor());
	}

	ToolRouter(ToolRegistry registry, ToolPolicy policy, ToolApprovalService approvalService,
			ExecutorService executor) {
		this.registry = registry;
		this.policy = policy;
		this.approvalService = approvalService;
		this.executor = executor;
	}

	public ToolResult invoke(ToolInvocation invocation) {
		ToolDefinition definition = registry.getTool(invocation.providerId(), invocation.toolName());
		ToolProvider provider = registry.getProvider(invocation.providerId());
		if (definition == null || provider == null) {
			return failure(invocation, "TOOL_NOT_FOUND", "Tool not found: "
				+ invocation.providerId() + "/" + invocation.toolName());
		}
		ToolPolicyDecision decision = policy.evaluate(definition, invocation);
		if (decision.action() == ToolPolicyAction.DENY) {
			return failure(invocation, "TOOL_DENIED", decision.reason());
		}
		String approvalId = null;
		if (decision.action() == ToolPolicyAction.REQUIRE_APPROVAL) {
			ToolApprovalDecision approval = approvalService.authorize(invocation, definition,
				decision.reason());
			approvalId = approval.approvalId();
			if (approval.approvalRequired()) {
				return waitingApproval(invocation, approvalId);
			}
		}

		Future<ToolResult> future = executor.submit(() -> provider.invoke(invocation));
		try {
			ToolResult result = future.get(invocation.timeout().toMillis(), TimeUnit.MILLISECONDS);
			if (result == null) {
				return withApproval(failure(invocation, "TOOL_INVALID_RESULT",
					"Tool provider returned no result"), approvalId);
			}
			return withApproval(result.withInvocation(invocation), approvalId);
		}
		catch (TimeoutException exception) {
			future.cancel(true);
			return withApproval(failure(invocation, "TOOL_TIMEOUT",
				"Tool invocation timed out"), approvalId);
		}
		catch (InterruptedException exception) {
			future.cancel(true);
			Thread.currentThread().interrupt();
			return withApproval(failure(invocation, "TOOL_INTERRUPTED",
				"Tool invocation was interrupted"), approvalId);
		}
		catch (ExecutionException exception) {
			return withApproval(failure(invocation, "TOOL_PROVIDER_ERROR",
				errorMessage(exception.getCause())), approvalId);
		}
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
