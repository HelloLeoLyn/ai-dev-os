package com.aidevos.orchestrator.tool;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record ToolInvocation(String executionId, String invocationId, String jobId, String workspace,
		String providerId, String toolName, Map<String, Object> arguments, Duration timeout) {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

	public ToolInvocation {
		if (executionId == null || executionId.isBlank()) {
			throw new IllegalArgumentException("Tool executionId is required");
		}
		invocationId = invocationId == null || invocationId.isBlank()
			? UUID.randomUUID().toString() : invocationId;
		if (providerId == null || providerId.isBlank()) {
			throw new IllegalArgumentException("Tool providerId is required");
		}
		if (toolName == null || toolName.isBlank()) {
			throw new IllegalArgumentException("Tool name is required");
		}
		arguments = arguments == null ? Map.of()
			: Map.copyOf(new LinkedHashMap<>(arguments));
		timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
		if (timeout.isZero() || timeout.isNegative()) {
			throw new IllegalArgumentException("Tool timeout must be positive");
		}
	}

	public ToolInvocation(String executionId, String invocationId, String providerId,
			String toolName, Map<String, Object> arguments, Duration timeout) {
		this(executionId, invocationId, null, null, providerId, toolName, arguments, timeout);
	}
}
