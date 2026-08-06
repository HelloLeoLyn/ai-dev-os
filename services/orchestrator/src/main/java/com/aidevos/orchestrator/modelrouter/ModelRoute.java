package com.aidevos.orchestrator.modelrouter;

/**
 * Read-only view of one routing rule: task type to provider (with the
 * provider's current model and enabled state resolved at read time).
 */
public record ModelRoute(
		String taskType,
		String providerId,
		String model,
		boolean enabled) {
}
