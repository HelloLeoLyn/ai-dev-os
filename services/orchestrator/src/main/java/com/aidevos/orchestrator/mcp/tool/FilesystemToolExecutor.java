package com.aidevos.orchestrator.mcp.tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Filesystem tool: safe read / list operations on local paths. Write
 * operations are reserved for a later MCP server phase.
 */
@Component
public class FilesystemToolExecutor implements McpToolExecutor {

	private static final long MAX_READ_BYTES = 256 * 1024;

	@Override
	public ToolDefinition definition() {
		return new ToolDefinition("filesystem", "Filesystem", ToolType.FILESYSTEM,
			"Read files and list directories on the local workspace",
			Map.of("path", "String", "operation", "read|list"),
			Set.of(ToolPermission.READ, ToolPermission.WRITE));
	}

	@Override
	public ToolExecutionResult execute(ToolExecutionRequest request) {
		String path = string(request, "path");
		if (path == null || path.isBlank()) {
			return ToolExecutionResult.failure("Missing required parameter: path", Map.of());
		}
		String operation = string(request, "operation");
		if (operation == null) {
			operation = "read";
		}
		try {
			Path target = Path.of(path);
			return switch (operation) {
				case "list" -> {
					List<String> entries = Files.list(target)
						.map(entry -> Files.isDirectory(entry) ? entry.getFileName() + "/"
							: entry.getFileName().toString())
						.sorted().toList();
					yield ToolExecutionResult.success(String.join("\n", entries),
						Map.of("path", path, "operation", operation));
				}
				case "read" -> {
					byte[] bytes = Files.readAllBytes(target);
					if (bytes.length > MAX_READ_BYTES) {
						yield ToolExecutionResult.failure(
							"File too large to read: " + bytes.length + " bytes", Map.of());
					}
					yield ToolExecutionResult.success(
						new String(bytes, StandardCharsets.UTF_8),
						Map.of("path", path, "operation", operation));
				}
				default -> ToolExecutionResult.failure(
					"Unsupported filesystem operation: " + operation, Map.of());
			};
		}
		catch (IOException | RuntimeException exception) {
			return ToolExecutionResult.failure(exception.getMessage(), Map.of("path", path));
		}
	}

	private String string(ToolExecutionRequest request, String key) {
		Object value = request.parameters().get(key);
		return value == null ? null : String.valueOf(value);
	}
}
