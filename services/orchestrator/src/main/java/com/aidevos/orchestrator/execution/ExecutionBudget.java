package com.aidevos.orchestrator.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Execution budget and stop conditions for a work package. Resolved from plan
 * snapshot planner metadata with conservative defaults. Once the acceptance
 * conditions in stopConditions are satisfied, execution must stop; already
 * passing tests are never re-run.
 */
public record ExecutionBudget(ValidationProfile validationProfile, int maxAiCalls,
		int maxToolRetries, List<String> stopConditions) {

	public static final int DEFAULT_MAX_AI_CALLS = 20;
	public static final int DEFAULT_MAX_TOOL_RETRIES = 1;

	public ExecutionBudget {
		validationProfile = validationProfile == null ? ValidationProfile.FAST : validationProfile;
		maxAiCalls = maxAiCalls <= 0 ? DEFAULT_MAX_AI_CALLS : maxAiCalls;
		maxToolRetries = maxToolRetries < 0 ? DEFAULT_MAX_TOOL_RETRIES : maxToolRetries;
		stopConditions = stopConditions == null ? List.of() : List.copyOf(stopConditions);
	}

	public static ExecutionBudget defaults() {
		return new ExecutionBudget(ValidationProfile.FAST, DEFAULT_MAX_AI_CALLS,
			DEFAULT_MAX_TOOL_RETRIES, List.of());
	}

	public static ExecutionBudget resolve(Map<String, Object> plannerMetadata) {
		if (plannerMetadata == null || plannerMetadata.isEmpty()) {
			return defaults();
		}
		return new ExecutionBudget(
			enumValue(plannerMetadata.get("validationProfile"), ValidationProfile.class,
				ValidationProfile.FAST),
			intValue(plannerMetadata.get("maxAiCalls"), DEFAULT_MAX_AI_CALLS),
			intValue(plannerMetadata.get("maxToolRetries"), DEFAULT_MAX_TOOL_RETRIES),
			stringList(plannerMetadata.get("stopConditions")));
	}

	private static <E extends Enum<E>> E enumValue(Object value, Class<E> type, E fallback) {
		if (value instanceof String text) {
			try {
				return Enum.valueOf(type, text.toUpperCase());
			}
			catch (IllegalArgumentException ignored) {
				return fallback;
			}
		}
		return fallback;
	}

	private static int intValue(Object value, int fallback) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value instanceof String text) {
			try {
				return Integer.parseInt(text);
			}
			catch (NumberFormatException ignored) {
				return fallback;
			}
		}
		return fallback;
	}

	private static List<String> stringList(Object value) {
		if (value instanceof List<?> list) {
			List<String> result = new ArrayList<>();
			for (Object item : list) {
				if (item != null) {
					result.add(String.valueOf(item));
				}
			}
			return List.copyOf(result);
		}
		if (value instanceof String text && !text.isBlank()) {
			return List.of(text.split(",")).stream().map(String::trim)
				.filter(condition -> !condition.isEmpty()).toList();
		}
		return List.of();
	}
}
