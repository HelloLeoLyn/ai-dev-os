package com.aidevos.orchestrator.tool;

import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.mcp.tool.McpToolRouter;
import com.aidevos.orchestrator.mcp.tool.ToolExecutionRequest;
import com.aidevos.orchestrator.mcp.tool.ToolExecutionResult;
import com.aidevos.orchestrator.mcp.tool.ToolPermission;
import com.aidevos.orchestrator.mcp.tool.ToolRegistry;
import org.springframework.stereotype.Component;

/** Makes the unified MCP registry visible and executable through Plan steps. */
@Component
public class UnifiedMcpToolProvider implements ToolProvider {

	public static final String ID = "unified-mcp";

	private final ToolRegistry registry;
	private final McpToolRouter router;

	public UnifiedMcpToolProvider(ToolRegistry registry, McpToolRouter router) {
		this.registry = registry;
		this.router = router;
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public List<ToolDefinition> getTools() {
		return registry.listTools().stream().map(tool -> new ToolDefinition(ID, tool.toolId(),
			tool.description(), tool.inputSchema(), access(tool.permission()))).toList();
	}

	@Override
	public ToolResult invoke(ToolInvocation invocation) {
		Map<String, Object> parameters = new java.util.LinkedHashMap<>(invocation.arguments());
		if (invocation.workspace() != null) parameters.putIfAbsent("workspace", invocation.workspace());
		ToolExecutionResult result = router.route(new ToolExecutionRequest(invocation.toolName(),
			null, null, parameters));
		if (!result.success()) {
			return ToolResult.failure("MCP_TOOL_FAILED",
				result.error() == null ? "MCP tool failed" : result.error());
		}
		return new ToolResult(invocation.executionId(), invocation.invocationId(), true, "OK",
			"Tool executed successfully", result.output(),
			List.of(ToolContent.text(invocation.toolName() + ".txt", result.output())),
			result.metadata());
	}

	private ToolAccess access(java.util.Set<ToolPermission> permissions) {
		return permissions.stream().allMatch(permission -> permission == ToolPermission.READ)
			? ToolAccess.READ_ONLY : ToolAccess.WORKSPACE_WRITE;
	}
}
