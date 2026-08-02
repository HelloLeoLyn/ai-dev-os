package com.aidevos.orchestrator.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PlanValues {

	private PlanValues() {
	}

	static Map<String, Object> freezeMap(Map<String, Object> source) {
		if (source == null || source.isEmpty()) {
			return Map.of();
		}
		Map<String, Object> copy = new LinkedHashMap<>();
		source.forEach((key, value) -> copy.put(key, freeze(value)));
		return Map.copyOf(copy);
	}

	private static Object freeze(Object value) {
		if (value instanceof Map<?, ?> source) {
			Map<String, Object> copy = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : source.entrySet()) {
				if (!(entry.getKey() instanceof String key)) {
					throw new IllegalArgumentException("Plan map keys must be strings");
				}
				copy.put(key, freeze(entry.getValue()));
			}
			return Map.copyOf(copy);
		}
		if (value instanceof List<?> source) {
			List<Object> copy = new ArrayList<>();
			source.forEach(item -> copy.add(freeze(item)));
			return List.copyOf(copy);
		}
		return value;
	}
}
