package com.aidevos.orchestrator.execution.system;

import java.util.Optional;

/**
 * Allowlist of system-internal deterministic actions executed by SYSTEM_STEP.
 * A SYSTEM_STEP never creates an AI job and never calls an LLM; the scheduler
 * routes it to the registered executor for the resolved action. Anything
 * outside this allowlist fails closed.
 */
public enum SystemAction {

	RUN_BOOKKEEPING("run-bookkeeping");

	private final String wireName;

	SystemAction(String wireName) {
		this.wireName = wireName;
	}

	/**
	 * Resolves a planner-declared system action name to the allowlist.
	 * Accepts both the wire name ("run-bookkeeping") and the enum constant.
	 * Returns empty for anything outside the allowlist.
	 */
	public static Optional<SystemAction> fromName(String actionName) {
		if (actionName == null || actionName.isBlank()) {
			return Optional.empty();
		}
		String normalized = actionName.trim().toLowerCase();
		for (SystemAction action : values()) {
			if (action.wireName.equals(normalized)
					|| action.name().equalsIgnoreCase(normalized)) {
				return Optional.of(action);
			}
		}
		return Optional.empty();
	}
}
