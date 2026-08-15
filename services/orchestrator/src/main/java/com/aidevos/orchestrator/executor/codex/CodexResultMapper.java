package com.aidevos.orchestrator.executor.codex;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class CodexResultMapper {

	private final ObjectMapper objectMapper;

	public CodexResultMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public CodexOutput map(String jsonLines) {
		String threadId = null;
		String summary = null;
		String structuredPayload = null;
		if (jsonLines == null) {
			return new CodexOutput(null, null, null);
		}
		for (String line : jsonLines.lines().toList()) {
			if (line.isBlank()) {
				continue;
			}
			try {
				JsonNode event = objectMapper.readTree(line);
				if ("thread.started".equals(text(event, "type"))) {
					threadId = text(event, "thread_id");
				}
				JsonNode item = event.get("item");
				if ("item.completed".equals(text(event, "type")) && item != null
						&& "agent_message".equals(text(item, "type"))) {
					String message = text(item, "text");
					summary = structuredSummary(message);
					structuredPayload = structuredPayload(message);
				}
			}
			catch (Exception ignored) {
				// Non-JSON lines are retained in the events artifact and do not break result mapping.
			}
		}
		return new CodexOutput(threadId, summary, structuredPayload);
	}

	private String structuredPayload(String text) {
		if (text == null) return null;
		try { return objectMapper.writeValueAsString(objectMapper.readTree(text)); }
		catch (Exception ignored) { return null; }
	}

	private String structuredSummary(String text) {
		if (text == null) {
			return null;
		}
		try {
			JsonNode result = objectMapper.readTree(text);
			String summary = text(result, "summary");
			return summary == null ? text : summary;
		}
		catch (Exception ignored) {
			return text;
		}
	}

	private String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value != null && value.isTextual() ? value.asText() : null;
	}
}
