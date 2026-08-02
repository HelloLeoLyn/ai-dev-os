package com.aidevos.orchestrator.tool;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

class FakeToolProvider implements ToolProvider {

	private final String id;
	private final List<ToolDefinition> tools;
	private final Function<ToolInvocation, ToolResult> handler;

	FakeToolProvider(String id, String toolName, Function<ToolInvocation, ToolResult> handler) {
		this.id = id;
		this.tools = List.of(new ToolDefinition(id, toolName, "Fake tool",
			Map.of("type", "object"), ToolAccess.READ_ONLY));
		this.handler = handler;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public List<ToolDefinition> getTools() {
		return tools;
	}

	@Override
	public ToolResult invoke(ToolInvocation invocation) {
		return handler.apply(invocation);
	}
}
