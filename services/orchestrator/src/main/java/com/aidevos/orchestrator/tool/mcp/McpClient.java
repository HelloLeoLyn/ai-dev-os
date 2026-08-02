package com.aidevos.orchestrator.tool.mcp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class McpClient implements AutoCloseable {

	public static final String PROTOCOL_VERSION = "2025-06-18";

	private final McpSession session;
	private final ObjectMapper objectMapper;
	private final Duration requestTimeout;
	private volatile boolean initialized;
	private volatile String negotiatedProtocolVersion;

	public McpClient(McpSession session, ObjectMapper objectMapper, Duration requestTimeout) {
		this.session = session;
		this.objectMapper = objectMapper;
		this.requestTimeout = requestTimeout;
	}

	public synchronized void initialize() {
		if (initialized) {
			return;
		}
		session.connect();
		JsonNode result = session.request("initialize", Map.of(
			"protocolVersion", PROTOCOL_VERSION,
			"capabilities", Map.of(),
			"clientInfo", Map.of("name", "ai-dev-os-orchestrator", "version", "1.0")),
			requestTimeout);
		String version = text(result, "protocolVersion");
		if (version == null || version.isBlank()) {
			throw new McpException("MCP_INITIALIZE_FAILED", "MCP server returned no protocol version");
		}
		JsonNode tools = result.path("capabilities").get("tools");
		if (tools == null || tools.isNull()) {
			throw new McpException("MCP_CAPABILITY_UNAVAILABLE", "MCP server does not support tools");
		}
		negotiatedProtocolVersion = version;
		session.notify("notifications/initialized", Map.of());
		initialized = true;
	}

	public List<McpToolDescriptor> listTools() {
		ensureInitialized();
		List<McpToolDescriptor> tools = new ArrayList<>();
		String cursor = null;
		do {
			Map<String, Object> parameters = cursor == null ? Map.of() : Map.of("cursor", cursor);
			JsonNode result = session.request("tools/list", parameters, requestTimeout);
			JsonNode entries = result.get("tools");
			if (entries == null || !entries.isArray()) {
				throw new McpException("MCP_INVALID_RESPONSE", "tools/list returned no tools array");
			}
			for (JsonNode entry : entries) {
				String name = text(entry, "name");
				if (name == null || name.isBlank()) {
					throw new McpException("MCP_INVALID_RESPONSE", "MCP tool has no name");
				}
				tools.add(new McpToolDescriptor(name, text(entry, "description"),
					objectMap(entry.get("inputSchema")), objectMap(entry.get("annotations"))));
			}
			cursor = text(result, "nextCursor");
		} while (cursor != null && !cursor.isBlank());
		return List.copyOf(tools);
	}

	public JsonNode callTool(String name, Map<String, Object> arguments, Duration timeout) {
		ensureInitialized();
		return session.request("tools/call", Map.of("name", name,
			"arguments", arguments == null ? Map.of() : arguments), timeout);
	}

	public String getNegotiatedProtocolVersion() {
		return negotiatedProtocolVersion;
	}

	private void ensureInitialized() {
		if (!initialized) {
			initialize();
		}
	}

	private Map<String, Object> objectMap(JsonNode node) {
		if (node == null || !node.isObject()) {
			return Map.of();
		}
		return new LinkedHashMap<>(objectMapper.convertValue(node,
			new TypeReference<Map<String, Object>>() { }));
	}

	private String text(JsonNode node, String field) {
		JsonNode value = node == null ? null : node.get(field);
		return value != null && value.isTextual() ? value.asText() : null;
	}

	@Override
	public synchronized void close() {
		initialized = false;
		session.close();
	}
}
