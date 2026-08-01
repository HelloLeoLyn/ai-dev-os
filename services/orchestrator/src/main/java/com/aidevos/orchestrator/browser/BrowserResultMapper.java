package com.aidevos.orchestrator.browser;

import java.util.ArrayList;
import java.util.List;

import com.aidevos.orchestrator.execution.ExecutionArtifact;
import com.aidevos.orchestrator.execution.ExecutionResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class BrowserResultMapper {

	private final ObjectMapper objectMapper;

	public BrowserResultMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void map(String output, ExecutionResult result) {
		if (output == null || output.isBlank()) {
			return;
		}
		try {
			JsonNode root = objectMapper.readTree(output);
			if (!root.isObject()) {
				return;
			}
			JsonNode resultOutput = root.get("output");
			if (resultOutput != null && resultOutput.isTextual()) {
				result.setOutput(resultOutput.asText());
			}
			result.setArtifacts(mapArtifacts(root.get("artifacts")));
		}
		catch (Exception ignored) {
			// Keep the original assistant text when an older Agent does not return the Browser envelope.
		}
	}

	private List<ExecutionArtifact> mapArtifacts(JsonNode artifactsNode) {
		List<ExecutionArtifact> artifacts = new ArrayList<>();
		if (artifactsNode == null || !artifactsNode.isArray()) {
			return artifacts;
		}
		for (JsonNode node : artifactsNode) {
			if (!node.isObject()) {
				continue;
			}
			ExecutionArtifact artifact = new ExecutionArtifact();
			artifact.setType(text(node, "type"));
			artifact.setName(text(node, "name"));
			artifact.setMediaType(text(node, "mediaType"));
			artifact.setUri(text(node, "uri"));
			artifact.setContent(text(node, "content"));
			artifacts.add(artifact);
		}
		return artifacts;
	}

	private String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value != null && value.isTextual() ? value.asText() : null;
	}
}
