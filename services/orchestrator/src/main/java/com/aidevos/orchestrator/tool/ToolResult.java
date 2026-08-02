package com.aidevos.orchestrator.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ToolResult(String executionId, String invocationId, boolean success,
		String code, String message, String output, List<ToolContent> content,
		Map<String, Object> metadata) {

	public ToolResult {
		content = content == null ? List.of() : List.copyOf(new ArrayList<>(content));
		metadata = metadata == null ? Map.of()
			: Map.copyOf(new LinkedHashMap<>(metadata));
	}

	public static ToolResult success(String output, List<ToolContent> content) {
		return new ToolResult(null, null, true, "OK", "Tool executed successfully",
			output, content, Map.of());
	}

	public static ToolResult failure(String code, String message) {
		return new ToolResult(null, null, false, code, message, null, List.of(), Map.of());
	}

	public ToolResult withInvocation(ToolInvocation invocation) {
		return new ToolResult(invocation.executionId(), invocation.invocationId(), success,
			code, message, output, content, metadata);
	}

	public boolean approvalRequired() {
		return Boolean.TRUE.equals(metadata.get("approvalRequired"));
	}

	public String approvalId() {
		Object value = metadata.get("approvalId");
		return value instanceof String text ? text : null;
	}
}
