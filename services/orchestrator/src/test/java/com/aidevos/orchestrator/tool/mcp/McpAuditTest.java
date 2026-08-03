package com.aidevos.orchestrator.tool.mcp;

import com.aidevos.orchestrator.audit.*;
import com.aidevos.orchestrator.tool.ToolInvocation;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class McpAuditTest {
	@Test
	void recordsSessionAndCallOutcomes() {
		InMemoryAuditRepository events = new InMemoryAuditRepository();
		McpToolProvider provider = provider(new AuditService(events));
		try {
			provider.getTools();
			assertTrue(provider.invoke(invocation("echo", Map.of("value", "READY"))).success());
			assertFalse(provider.invoke(invocation("protocol_error", Map.of())).success());

			assertEquals(List.of(EventType.MCP_SESSION_STARTED, EventType.MCP_CALL_STARTED,
				EventType.MCP_CALL_COMPLETED, EventType.MCP_CALL_STARTED,
				EventType.MCP_CALL_FAILED), events.query(EventQuery.all()).stream()
				.map(EventRecord::type).toList());
		} finally {
			provider.close();
		}
	}

	private McpToolProvider provider(AuditService audit) {
		ObjectMapper mapper = new ObjectMapper();
		String script = Path.of("src/test/resources/mcp/fake-mcp-server.js")
			.toAbsolutePath().normalize().toString();
		McpSession session = new McpStdioSession(List.of("node", script), null, mapper);
		return new McpToolProvider("filesystem",
			new McpClient(session, mapper, Duration.ofSeconds(2)), mapper, audit);
	}

	private ToolInvocation invocation(String tool, Map<String, Object> arguments) {
		return new ToolInvocation("execution-1", java.util.UUID.randomUUID().toString(),
			"filesystem", tool, arguments, Duration.ofSeconds(1));
	}
}
