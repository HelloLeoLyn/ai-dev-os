package com.aidevos.orchestrator.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.Yaml;

/**
 * Shared YAML configuration support used by the configuration loaders
 * (agents, models, MCP plugins, skills, agent market). Provides classpath
 * YAML reading, null-safe scalar/list/map conversions and small validation
 * helpers; loaders keep their domain-specific mapping and error handling.
 */
public final class YamlConfigSupport {

	private YamlConfigSupport() {
	}

	/**
	 * Reads a classpath YAML file into a string-keyed map. Empty documents are
	 * treated as an empty map.
	 */
	public static Map<String, Object> load(String configFile) {
		try (InputStream inputStream = YamlConfigSupport.class.getClassLoader()
				.getResourceAsStream(configFile)) {
			if (inputStream == null) {
				throw new IllegalStateException("Configuration file not found: " + configFile);
			}
			Object config = new Yaml().load(inputStream);
			return config instanceof Map<?, ?> map ? toObjectMap(map) : new LinkedHashMap<>();
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to read configuration file: " + configFile,
				exception);
		}
	}

	/**
	 * Converts a YAML list of maps into a list of string-keyed maps. Rejects
	 * non-list values ("Invalid {collection} configuration") and non-map
	 * entries ("Invalid {item} definition").
	 */
	public static List<Map<String, Object>> asList(Object value, String collection, String item) {
		if (!(value instanceof List<?> values)) {
			throw new IllegalStateException("Invalid " + collection + " configuration");
		}
		List<Map<String, Object>> result = new ArrayList<>();
		for (Object element : values) {
			if (!(element instanceof Map<?, ?> map)) {
				throw new IllegalStateException("Invalid " + item + " definition");
			}
			result.add(toObjectMap(map));
		}
		return result;
	}

	/**
	 * Converts a YAML map into a string-keyed map. Rejects non-map values.
	 */
	public static Map<String, Object> asMap(Object value, String what) {
		if (!(value instanceof Map<?, ?> map)) {
			throw new IllegalStateException("Invalid " + what + " configuration");
		}
		return toObjectMap(map);
	}

	/**
	 * Converts a YAML map into a string-keyed map; a missing value yields an
	 * empty map (used for optional nested configuration).
	 */
	public static Map<String, Object> objectMap(Object value, String what) {
		if (value == null) {
			return new LinkedHashMap<>();
		}
		return asMap(value, what);
	}

	/**
	 * Null-safe string value; non-string scalars are converted with
	 * {@code String.valueOf}.
	 */
	public static String string(Map<String, Object> map, String key) {
		Object value = map.get(key);
		return value == null ? null : String.valueOf(value);
	}

	/**
	 * Boolean flag with a default when the key is absent.
	 */
	public static boolean bool(Map<String, Object> map, String key, boolean defaultValue) {
		return map.containsKey(key) ? Boolean.TRUE.equals(map.get(key)) : defaultValue;
	}

	/**
	 * String list conversion. A missing or non-list value yields
	 * {@code defaultValue} (null or an empty list); a non-string entry raises
	 * "Invalid {what} configuration".
	 */
	public static List<String> stringList(Object value, String what, List<String> defaultValue) {
		if (!(value instanceof List<?> values)) {
			return defaultValue;
		}
		List<String> result = new ArrayList<>();
		for (Object element : values) {
			if (!(element instanceof String string)) {
				throw new IllegalStateException("Invalid " + what + " configuration");
			}
			result.add(string);
		}
		return result;
	}

	/**
	 * Rejects blank values with "{what} is required".
	 */
	public static void require(String value, String what) {
		if (isBlank(value)) {
			throw new IllegalStateException(what + " is required");
		}
	}

	/**
	 * Rejects duplicate identifiers with "Duplicate {what}: {value}".
	 */
	public static void requireUnique(Set<String> seen, String value, String what) {
		if (!seen.add(value)) {
			throw new IllegalStateException("Duplicate " + what + ": " + value);
		}
	}

	public static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	public static Set<String> newIdentitySet() {
		return new HashSet<>();
	}

	private static Map<String, Object> toObjectMap(Map<?, ?> map) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (!(entry.getKey() instanceof String key)) {
				throw new IllegalStateException("Invalid configuration key");
			}
			result.put(key, entry.getValue());
		}
		return result;
	}
}
