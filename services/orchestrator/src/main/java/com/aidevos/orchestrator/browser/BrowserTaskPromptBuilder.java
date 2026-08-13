package com.aidevos.orchestrator.browser;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class BrowserTaskPromptBuilder {

	private static final Set<String> SUPPORTED_ACTIONS = Set.of(
		"navigate", "snapshot", "click", "input", "select", "wait", "screenshot", "assert");

	private final ObjectMapper objectMapper;

	public BrowserTaskPromptBuilder(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public boolean supports(Map<String, Object> parameters) {
		return parameters != null && parameters.get("browser") instanceof Map<?, ?>;
	}

	public String build(String description, Map<String, Object> parameters) {
		Map<String, Object> browser = browserParameters(parameters);
		String action = requiredString(browser, "action").toLowerCase(Locale.ROOT);
		if (!SUPPORTED_ACTIONS.contains(action)) {
			throw new IllegalArgumentException("Unsupported browser action: " + action);
		}

		Map<String, Object> operation = new LinkedHashMap<>(browser);
		operation.put("action", action);
		return """
			Use the browser tool to perform exactly the browser operation below.
			Do not use shell commands or a separate browser driver.
			Task description: %s
			Explicit inputs from approved predecessor artifacts: %s
			Browser operation: %s

			Return only one JSON object with this shape:
			{"output":"short result summary","artifacts":[{"type":"screenshot","name":"screenshot.png","mediaType":"image/png","uri":"path-or-uri"}]}
			Use an empty artifacts array when the operation produces no file. Preserve any screenshot path or URI returned by the browser tool.
			""".formatted(description == null ? "" : description,
				writeJson(inputParameters(parameters)), writeJson(operation));
	}

	private Map<String, Object> inputParameters(Map<String, Object> parameters) {
		if (parameters == null || !(parameters.get("inputs") instanceof Map<?, ?> source)) {
			return Map.of();
		}
		Map<String, Object> inputs = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : source.entrySet()) {
			if (entry.getKey() instanceof String key) {
				inputs.put(key, entry.getValue());
			}
		}
		return inputs;
	}

	private Map<String, Object> browserParameters(Map<String, Object> parameters) {
		if (parameters == null || !(parameters.get("browser") instanceof Map<?, ?> source)) {
			throw new IllegalArgumentException("Browser task parameters are required");
		}
		Map<String, Object> browser = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : source.entrySet()) {
			if (entry.getKey() instanceof String key) {
				browser.put(key, entry.getValue());
			}
		}
		return browser;
	}

	private String requiredString(Map<String, Object> values, String field) {
		if (values.get(field) instanceof String value && !value.isBlank()) {
			return value;
		}
		throw new IllegalArgumentException("Browser " + field + " is required");
	}

	private String writeJson(Map<String, Object> value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (Exception exception) {
			throw new IllegalArgumentException("Browser parameters cannot be serialized", exception);
		}
	}
}
