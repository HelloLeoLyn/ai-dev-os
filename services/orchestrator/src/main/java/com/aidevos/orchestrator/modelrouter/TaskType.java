package com.aidevos.orchestrator.modelrouter;

/**
 * Task types used by the model router to pick a provider. GENERAL is the
 * fallback for unknown or unspecified task types.
 */
public enum TaskType {
	TASK_ANALYSIS,
	CODE_GENERATION,
	BROWSER_TEST,
	TEST_VERIFY,
	GENERAL;

	public static TaskType from(String value) {
		if (value == null || value.isBlank()) {
			return GENERAL;
		}
		try {
			return valueOf(value.trim().toUpperCase());
		}
		catch (IllegalArgumentException exception) {
			return GENERAL;
		}
	}
}
