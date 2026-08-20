package com.aidevos.orchestrator.execution.tool;

import java.util.Optional;

/**
 * Deterministic tool kinds executed by ToolExecutionService without an LLM.
 */
public enum DeterministicTool {
	GIT,
	MAVEN,
	NPM,
	SHELL,
	HTTP_HEALTH,
	WORKSPACE,
	VALIDATION;

	/**
	 * Resolves a planner-declared tool name to the deterministic tool allowlist.
	 * Returns empty for anything outside the allowlist.
	 */
	public static Optional<DeterministicTool> fromName(String toolName) {
		if (toolName == null || toolName.isBlank()) {
			return Optional.empty();
		}
		return switch (toolName.toLowerCase()) {
			case "git" -> Optional.of(GIT);
			case "maven", "mvn" -> Optional.of(MAVEN);
			case "npm" -> Optional.of(NPM);
			case "shell", "sh" -> Optional.of(SHELL);
			case "http", "health", "http_health" -> Optional.of(HTTP_HEALTH);
			case "workspace" -> Optional.of(WORKSPACE);
			case "validation" -> Optional.of(VALIDATION);
			default -> Optional.empty();
		};
	}
}
