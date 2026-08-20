package com.aidevos.orchestrator.execution.system;

import java.util.Map;

/**
 * Outcome of a system action. A failed result terminates the SYSTEM_STEP;
 * an unknown or unregistered action fails closed before any executor runs.
 */
public record SystemActionResult(boolean success, String message,
		Map<String, Object> metadata) {

	public SystemActionResult {
		metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
	}

	public static SystemActionResult ok(String message) {
		return new SystemActionResult(true, message, Map.of());
	}

	public static SystemActionResult failed(String message) {
		return new SystemActionResult(false, message, Map.of());
	}
}
