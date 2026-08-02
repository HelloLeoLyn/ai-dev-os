package com.aidevos.orchestrator.tool;

import java.util.List;

public interface ToolProvider {

	String getId();

	List<ToolDefinition> getTools();

	ToolResult invoke(ToolInvocation invocation);
}
