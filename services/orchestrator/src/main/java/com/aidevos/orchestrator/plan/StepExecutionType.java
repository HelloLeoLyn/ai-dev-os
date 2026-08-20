package com.aidevos.orchestrator.plan;

/**
 * How a plan step is executed. The scheduler routes on this type:
 * AI_STEP goes through the agent/LLM executor, TOOL_STEP and SYSTEM_STEP run
 * deterministically without an LLM, HUMAN_GATE pauses for external approval.
 * Old steps default to AI_STEP for backward compatibility.
 */
public enum StepExecutionType {
	AI_STEP,
	TOOL_STEP,
	SYSTEM_STEP,
	HUMAN_GATE
}
