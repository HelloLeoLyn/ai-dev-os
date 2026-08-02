package com.aidevos.orchestrator.tool;

import java.util.LinkedHashMap;
import java.util.Map;

public record ToolContent(String type, String name, String mediaType, String uri,
		String content, Map<String, Object> metadata) {

	public ToolContent {
		metadata = metadata == null ? Map.of()
			: Map.copyOf(new LinkedHashMap<>(metadata));
	}

	public static ToolContent text(String name, String content) {
		return new ToolContent("tool-text", name, "text/plain", null, content, Map.of());
	}
}
