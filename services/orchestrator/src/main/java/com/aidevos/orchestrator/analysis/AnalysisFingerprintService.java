package com.aidevos.orchestrator.analysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AnalysisFingerprintService {
	private final ObjectMapper mapper;
	public AnalysisFingerprintService(ObjectMapper mapper) { this.mapper = mapper; }
	public String fingerprint(String taskId, String executionId, String extractorVersion,
			JsonNode payload) {
		try {
			String canonical = mapper.writeValueAsString(canonical(payload));
			String source = taskId + "\n" + executionId + "\n" + extractorVersion + "\n" + canonical;
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(source.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception) {
			throw new IllegalStateException("Unable to fingerprint analysis payload", exception);
		}
	}
	private Object canonical(JsonNode node) {
		if (node.isObject()) {
			Map<String, Object> values = new TreeMap<>();
			node.properties().forEach(entry -> values.put(entry.getKey(), canonical(entry.getValue())));
			return values;
		}
		if (node.isArray()) {
			return java.util.stream.StreamSupport.stream(node.spliterator(), false)
				.map(this::canonical).toList();
		}
		if (node.isNull()) return null;
		if (node.isBoolean()) return node.asBoolean();
		if (node.isIntegralNumber()) return node.asLong();
		if (node.isFloatingPointNumber()) return node.asDouble();
		return node.asText();
	}
}
