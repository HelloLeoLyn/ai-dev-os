package com.aidevos.orchestrator.planner;

import java.util.LinkedHashMap;
import java.util.Map;

import com.aidevos.orchestrator.plan.PlanSnapshot;

public record PlanningRequest(String requestId, String goal, String plannerName, String model,
		String promptVersion, Map<String, Object> structuredInput, PlanSnapshot snapshot,
		Map<String, Object> metadata) {

	public PlanningRequest {
		structuredInput = immutableMap(structuredInput);
		metadata = immutableMap(metadata);
	}

	private static Map<String, Object> immutableMap(Map<String, Object> source) {
		return source == null || source.isEmpty()
			? Map.of() : Map.copyOf(new LinkedHashMap<>(source));
	}
}
