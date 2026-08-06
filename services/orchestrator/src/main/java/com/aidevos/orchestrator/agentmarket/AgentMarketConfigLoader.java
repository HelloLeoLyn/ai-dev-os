package com.aidevos.orchestrator.agentmarket;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads agents-market.yaml into AgentPackage definitions. Mirrors
 * AgentConfigLoader; read-only catalog configuration used by the agent market
 * registry.
 */
@Component
public class AgentMarketConfigLoader {

	private static final String CONFIG_FILE = "agents-market.yaml";

	public List<AgentPackage> loadPackages() {
		try (InputStream inputStream = getClass().getClassLoader()
				.getResourceAsStream(CONFIG_FILE)) {
			if (inputStream == null) {
				throw new IllegalStateException("Configuration file not found: " + CONFIG_FILE);
			}
			Map<String, Object> config = new Yaml().load(inputStream);
			return toPackages(config.get("agents"));
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to read configuration file: " + CONFIG_FILE,
				exception);
		}
	}

	List<AgentPackage> toPackages(Object value) {
		if (!(value instanceof List<?> packageValues)) {
			throw new IllegalStateException("Invalid agent market configuration");
		}
		List<AgentPackage> packages = new ArrayList<>();
		Set<String> agentIds = new HashSet<>();
		for (Object packageValue : packageValues) {
			if (!(packageValue instanceof Map<?, ?> map)) {
				throw new IllegalStateException("Invalid agent package definition");
			}
			AgentPackage agentPackage = toPackage(map);
			if (isBlank(agentPackage.getAgentId())) {
				throw new IllegalStateException("agentId is required");
			}
			if (!agentIds.add(agentPackage.getAgentId())) {
				throw new IllegalStateException("Duplicate agentId: " + agentPackage.getAgentId());
			}
			packages.add(agentPackage);
		}
		return packages;
	}

	private AgentPackage toPackage(Map<?, ?> map) {
		boolean enabled = !map.containsKey("enabled") || Boolean.TRUE.equals(map.get("enabled"));
		return new AgentPackage(string(map, "agentId"), string(map, "name"),
			string(map, "version"), string(map, "description"), string(map, "author"),
			toStringList(map.get("capabilities")), toStringList(map.get("skills")),
			toStringList(map.get("plugins")), string(map, "executor"),
			toObjectMap(map.get("executorConfig")), enabled, false);
	}

	private List<String> toStringList(Object value) {
		if (!(value instanceof List<?> values)) {
			return List.of();
		}
		List<String> strings = new ArrayList<>();
		for (Object item : values) {
			if (!(item instanceof String string)) {
				throw new IllegalStateException("Invalid agent package list configuration");
			}
			strings.add(string);
		}
		return strings;
	}

	private Map<String, Object> toObjectMap(Object value) {
		Map<String, Object> config = new LinkedHashMap<>();
		if (!(value instanceof Map<?, ?> values)) {
			return config;
		}
		for (Map.Entry<?, ?> entry : values.entrySet()) {
			if (!(entry.getKey() instanceof String key)) {
				throw new IllegalStateException("Invalid executor configuration key");
			}
			config.put(key, entry.getValue());
		}
		return config;
	}

	private String string(Map<?, ?> map, String key) {
		Object value = map.get(key);
		return value == null ? null : String.valueOf(value);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
