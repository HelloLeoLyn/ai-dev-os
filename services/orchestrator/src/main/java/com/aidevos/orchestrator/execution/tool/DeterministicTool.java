package com.aidevos.orchestrator.execution.tool;

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
	VALIDATION
}
