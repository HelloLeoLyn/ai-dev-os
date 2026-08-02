package com.aidevos.orchestrator.tool.mcp;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpClientTest {

	@Test
	void shouldInitializeNegotiateCapabilitiesAndListTools() {
		McpClient client = client(Duration.ofSeconds(2));

		client.initialize();
		List<McpToolDescriptor> tools = client.listTools();

		assertEquals(McpClient.PROTOCOL_VERSION, client.getNegotiatedProtocolVersion());
		assertEquals(4, tools.size());
		assertEquals("echo", tools.getFirst().name());
		assertEquals(true, tools.getFirst().annotations().get("readOnlyHint"));
		client.close();
	}

	@Test
	void shouldCallToolAndDisconnect() {
		ObjectMapper objectMapper = new ObjectMapper();
		McpStdioSession session = session(objectMapper);
		McpClient client = new McpClient(session, objectMapper, Duration.ofSeconds(2));

		JsonNode result = client.callTool("echo", Map.of("value", "READY"),
			Duration.ofSeconds(2));

		assertEquals("READY", result.path("content").get(0).path("text").asText());
		assertTrue(session.isConnected());
		client.close();
		assertFalse(session.isConnected());
	}

	@Test
	void shouldReportUnavailableServer() {
		ObjectMapper objectMapper = new ObjectMapper();
		McpClient client = new McpClient(new McpStdioSession(
			List.of("missing-mcp-server-executable"), null, objectMapper), objectMapper,
			Duration.ofMillis(100));

		McpException exception = assertThrows(McpException.class, client::initialize);

		assertEquals("MCP_SERVER_UNAVAILABLE", exception.getCode());
	}

	@Test
	void shouldReportRequestTimeoutAndProtocolError() {
		McpClient client = client(Duration.ofSeconds(2));

		McpException timeout = assertThrows(McpException.class,
			() -> client.callTool("slow", Map.of(), Duration.ofMillis(20)));
		McpException protocol = assertThrows(McpException.class,
			() -> client.callTool("protocol_error", Map.of(), Duration.ofSeconds(1)));

		assertEquals("MCP_TIMEOUT", timeout.getCode());
		assertEquals("MCP_PROTOCOL_ERROR", protocol.getCode());
		client.close();
	}

	private McpClient client(Duration timeout) {
		ObjectMapper objectMapper = new ObjectMapper();
		return new McpClient(session(objectMapper), objectMapper, timeout);
	}

	private McpStdioSession session(ObjectMapper objectMapper) {
		String script = Path.of("src/test/resources/mcp/fake-mcp-server.js")
			.toAbsolutePath().normalize().toString();
		return new McpStdioSession(List.of("node", script), null, objectMapper);
	}
}
