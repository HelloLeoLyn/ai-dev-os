package com.aidevos.orchestrator.executor;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.tool.ToolArtifactMapper;
import com.aidevos.orchestrator.tool.ToolInvocation;
import com.aidevos.orchestrator.tool.ToolResult;
import com.aidevos.orchestrator.tool.ToolRouter;
import org.springframework.stereotype.Component;

@Component
public class ToolExecutor implements AgentExecutor {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

	private final ToolRouter toolRouter;
	private final ToolArtifactMapper artifactMapper;

	public ToolExecutor(ToolRouter toolRouter, ToolArtifactMapper artifactMapper) {
		this.toolRouter = toolRouter;
		this.artifactMapper = artifactMapper;
	}

	@Override
	public String getType() {
		return "tool";
	}

	@Override
	public ExecutionResult execute(ExecutionContext context) {
		try {
			ToolInvocation invocation = invocation(context);
			ToolResult toolResult = toolRouter.invoke(invocation);
			ExecutionResult result = new ExecutionResult();
			result.setSuccess(toolResult.success());
			result.setMessage(toolResult.message());
			result.setOutput(toolResult.output());
			result.setArtifacts(artifactMapper.map(invocation, toolResult));
			result.setApprovalRequired(toolResult.approvalRequired());
			result.setApprovalId(toolResult.approvalId());
			result.getMetadata().put("toolExecutionId", invocation.executionId());
			result.getMetadata().put("toolInvocationId", invocation.invocationId());
			result.getMetadata().put("toolProviderId", invocation.providerId());
			result.getMetadata().put("toolName", invocation.toolName());
			result.getMetadata().put("toolResultCode", toolResult.code());
			return result;
		}
		catch (IllegalArgumentException exception) {
			ExecutionResult result = new ExecutionResult();
			result.setSuccess(false);
			result.setMessage("Invalid tool invocation: " + exception.getMessage());
			result.getMetadata().put("toolResultCode", "INVALID_TOOL_INVOCATION");
			return result;
		}
	}

	private ToolInvocation invocation(ExecutionContext context) {
		Map<String, Object> tool = toolParameters(context);
		String provider = requiredAlias(tool, "provider", "server");
		String name = required(tool, "name");
		String invocationId = string(tool, "invocationId");
		if (invocationId == null && context.getJobId() != null) {
			invocationId = context.getJobId() + ":tool";
		}
		return new ToolInvocation(context.getExecutionId(), invocationId, context.getJobId(),
			context.getWorkspace(), provider, name, arguments(tool.get("arguments")),
			timeout(tool.get("timeout")));
	}

	private Map<String, Object> toolParameters(ExecutionContext context) {
		Object value = context.getParameters().get("tool");
		if (!(value instanceof Map<?, ?> source)) {
			throw new IllegalArgumentException("parameters.tool is required");
		}
		Map<String, Object> values = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : source.entrySet()) {
			if (entry.getKey() instanceof String key) {
				values.put(key, entry.getValue());
			}
		}
		return values;
	}

	private Map<String, Object> arguments(Object value) {
		if (value == null) {
			return Map.of();
		}
		if (!(value instanceof Map<?, ?> source)) {
			throw new IllegalArgumentException("tool.arguments must be an object");
		}
		Map<String, Object> values = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : source.entrySet()) {
			if (!(entry.getKey() instanceof String key)) {
				throw new IllegalArgumentException("tool.arguments keys must be strings");
			}
			values.put(key, entry.getValue());
		}
		return values;
	}

	private Duration timeout(Object value) {
		if (value == null) {
			return DEFAULT_TIMEOUT;
		}
		if (!(value instanceof String text) || text.isBlank()) {
			throw new IllegalArgumentException("tool.timeout must be an ISO-8601 duration");
		}
		try {
			return Duration.parse(text);
		}
		catch (DateTimeParseException exception) {
			throw new IllegalArgumentException("tool.timeout must be an ISO-8601 duration");
		}
	}

	private String requiredAlias(Map<String, Object> values, String primary, String alias) {
		String primaryValue = string(values, primary);
		String aliasValue = string(values, alias);
		if (primaryValue != null && aliasValue != null && !primaryValue.equals(aliasValue)) {
			throw new IllegalArgumentException("tool." + primary + " and tool." + alias + " conflict");
		}
		String value = primaryValue == null ? aliasValue : primaryValue;
		if (value == null) {
			throw new IllegalArgumentException("tool." + primary + " is required");
		}
		return value;
	}

	private String required(Map<String, Object> values, String key) {
		String value = string(values, key);
		if (value == null) {
			throw new IllegalArgumentException("tool." + key + " is required");
		}
		return value;
	}

	private String string(Map<String, Object> values, String key) {
		Object value = values.get(key);
		return value instanceof String text && !text.isBlank() ? text : null;
	}
}
