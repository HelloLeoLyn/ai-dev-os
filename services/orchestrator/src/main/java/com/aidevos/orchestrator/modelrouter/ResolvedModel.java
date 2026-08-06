package com.aidevos.orchestrator.modelrouter;

/**
 * Result of routing a task type to a concrete provider/model.
 */
public record ResolvedModel(
		TaskType taskType,
		String providerId,
		String providerName,
		String type,
		String model,
		boolean enabled) {
}
