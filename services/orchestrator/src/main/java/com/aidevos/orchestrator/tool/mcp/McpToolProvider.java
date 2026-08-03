package com.aidevos.orchestrator.tool.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.tool.ToolAccess;
import com.aidevos.orchestrator.tool.ToolContent;
import com.aidevos.orchestrator.tool.ToolDefinition;
import com.aidevos.orchestrator.tool.ToolInvocation;
import com.aidevos.orchestrator.tool.ToolProvider;
import com.aidevos.orchestrator.tool.ToolResult;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class McpToolProvider implements ToolProvider, AutoCloseable {

	private final String id;
	private final McpClient client;
	private final ObjectMapper objectMapper;
	private final AuditService auditService;
	private volatile List<ToolDefinition> tools;

	public McpToolProvider(String id, McpClient client, ObjectMapper objectMapper) {
		this(id, client, objectMapper, AuditService.noop());
	}

	public McpToolProvider(String id, McpClient client, ObjectMapper objectMapper,
			AuditService auditService) {
		this.id = id;
		this.client = client;
		this.objectMapper = objectMapper;
		this.auditService = auditService;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public synchronized List<ToolDefinition> getTools() {
		if (tools == null) {
			client.initialize();
			auditService.mcpEvent(EventType.MCP_SESSION_STARTED, id, null, "STARTED");
			tools = client.listTools().stream().map(this::definition).toList();
		}
		return tools;
	}

	@Override
	public ToolResult invoke(ToolInvocation invocation) {
		auditService.mcpEvent(EventType.MCP_CALL_STARTED, id, invocation, "RUNNING");
		try {
			JsonNode response = client.callTool(invocation.toolName(), invocation.arguments(),
				invocation.timeout());
			boolean error = response.path("isError").asBoolean(false);
			List<ToolContent> content = content(response);
			String output = content.stream().filter(item -> item.content() != null)
				.map(ToolContent::content).findFirst().orElse(null);
			if (error) {
				auditService.mcpEvent(EventType.MCP_CALL_FAILED, id, invocation, "FAILED");
				return new ToolResult(null, null, false, "MCP_TOOL_ERROR",
					output == null ? "MCP tool returned an error" : output, output, content,
					metadata());
			}
			auditService.mcpEvent(EventType.MCP_CALL_COMPLETED, id, invocation, "COMPLETED");
			return new ToolResult(null, null, true, "OK", "Tool executed successfully",
				output, content, metadata());
		}
		catch (McpException exception) {
			auditService.mcpEvent(EventType.MCP_CALL_FAILED, id, invocation, "FAILED");
			return ToolResult.failure(exception.getCode(), exception.getMessage());
		}
		catch (RuntimeException exception) {
			auditService.mcpEvent(EventType.MCP_CALL_FAILED, id, invocation, "FAILED");
			throw exception;
		}
	}

	private ToolDefinition definition(McpToolDescriptor descriptor) {
		Object readOnly = descriptor.annotations().get("readOnlyHint");
		ToolAccess access = Boolean.TRUE.equals(readOnly)
			? ToolAccess.READ_ONLY : ToolAccess.WORKSPACE_WRITE;
		return new ToolDefinition(id, descriptor.name(), descriptor.description(),
			descriptor.inputSchema(), access);
	}

	private List<ToolContent> content(JsonNode response) {
		List<ToolContent> items = new ArrayList<>();
		JsonNode content = response.get("content");
		if (content != null && content.isArray()) {
			int index = 0;
			for (JsonNode node : content) {
				items.add(content(node, index++));
			}
		}
		JsonNode structured = response.get("structuredContent");
		if (structured != null && !structured.isNull()) {
			items.add(new ToolContent("mcp-structured-output", "structured-output.json",
				"application/json", null, structured.toString(), Map.of()));
		}
		return List.copyOf(items);
	}

	private ToolContent content(JsonNode node, int index) {
		String type = node.path("type").asText("unknown");
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("mcpContentType", type);
		return switch (type) {
			case "text" -> new ToolContent("mcp-text", "mcp-text-" + index + ".txt",
				"text/plain", null, node.path("text").asText(), metadata);
			case "image", "audio" -> new ToolContent("mcp-" + type, "mcp-" + type + "-" + index,
				node.path("mimeType").asText("application/octet-stream"), null,
				node.path("data").asText(), metadata);
			case "resource_link" -> new ToolContent("mcp-resource-link",
				node.path("name").asText("resource-" + index), node.path("mimeType").asText(null),
				node.path("uri").asText(null), null, metadata);
			case "resource" -> embeddedResource(node.path("resource"), index, metadata);
			default -> new ToolContent("mcp-unknown", "mcp-content-" + index + ".json",
				"application/json", null, node.toString(), metadata);
		};
	}

	private ToolContent embeddedResource(JsonNode resource, int index, Map<String, Object> metadata) {
		String mediaType = resource.path("mimeType").asText("application/octet-stream");
		String content = resource.has("text") ? resource.path("text").asText()
			: resource.path("blob").asText(null);
		return new ToolContent("mcp-resource", "mcp-resource-" + index, mediaType,
			resource.path("uri").asText(null), content, metadata);
	}

	private Map<String, Object> metadata() {
		return Map.of("protocolVersion", client.getNegotiatedProtocolVersion());
	}

	@Override
	public void close() {
		client.close();
	}
}
