package com.aidevos.orchestrator.tool.mcp;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.tool.ToolAccess;
import com.aidevos.orchestrator.tool.ToolDefinition;
import com.aidevos.orchestrator.tool.ToolInvocation;
import com.aidevos.orchestrator.tool.ToolRegistry;
import com.aidevos.orchestrator.tool.ToolResult;
import com.aidevos.orchestrator.tool.ToolRouter;
import com.aidevos.orchestrator.tool.policy.AllowRegisteredToolPolicy;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolProviderTest {

	@Test
	void shouldConvertDefinitionsAndSuccessfulResult() {
		McpToolProvider provider = provider();

		List<ToolDefinition> tools = provider.getTools();
		ToolResult result = provider.invoke(invocation("echo", Map.of("value", "READY"),
			Duration.ofSeconds(1)));

		assertEquals(ToolAccess.READ_ONLY, tools.getFirst().access());
		assertEquals(ToolAccess.WORKSPACE_WRITE, tools.get(1).access());
		assertTrue(result.success());
		assertEquals("READY", result.output());
		assertEquals(2, result.content().size());
		provider.close();
	}

	@Test
	void shouldConvertParameterAndMcpErrors() {
		McpToolProvider provider = provider();

		ToolResult parameterError = provider.invoke(invocation("echo", Map.of(),
			Duration.ofSeconds(1)));
		ToolResult protocolError = provider.invoke(invocation("protocol_error", Map.of(),
			Duration.ofSeconds(1)));

		assertFalse(parameterError.success());
		assertEquals("MCP_TOOL_ERROR", parameterError.code());
		assertEquals("value must be a string", parameterError.message());
		assertEquals("MCP_PROTOCOL_ERROR", protocolError.code());
		provider.close();
	}

	@Test
	void shouldRequireApprovalForWriteToolAtToolPolicyBoundary() {
		McpToolProvider provider = provider();
		ToolRouter router = new ToolRouter(new ToolRegistry(List.of(provider)),
			new AllowRegisteredToolPolicy());

		ToolResult result = router.invoke(invocation("write_forbidden", Map.of("value", "x"),
			Duration.ofSeconds(1)));

		assertFalse(result.success());
		assertEquals("TOOL_APPROVAL_REQUIRED", result.code());
		assertTrue(result.approvalRequired());
		router.close();
		provider.close();
	}

	private ToolInvocation invocation(String tool, Map<String, Object> arguments, Duration timeout) {
		return new ToolInvocation("execution-1", "invocation-1", "filesystem",
			tool, arguments, timeout);
	}

	private McpToolProvider provider() {
		ObjectMapper objectMapper = new ObjectMapper();
		String script = Path.of("src/test/resources/mcp/fake-mcp-server.js")
			.toAbsolutePath().normalize().toString();
		McpSession session = new McpStdioSession(List.of("node", script), null, objectMapper);
		return new McpToolProvider("filesystem",
			new McpClient(session, objectMapper, Duration.ofSeconds(2)), objectMapper);
	}
}
