package com.aidevos.orchestrator.agent;

/**
 * The agent kinds that can appear as execution graph nodes. Each type maps to
 * one execution role: HERMES plans, CODEX modifies the workspace, OPENCLAW
 * drives the browser, TEST_AGENT verifies with tests and REPAIR_AGENT
 * analyses failures.
 */
public enum AgentType {
	HERMES,
	CODEX,
	OPENCLAW,
	TEST_AGENT,
	REPAIR_AGENT
}
