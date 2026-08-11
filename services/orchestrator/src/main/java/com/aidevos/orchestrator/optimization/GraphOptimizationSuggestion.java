package com.aidevos.orchestrator.optimization;

/**
 * A read-only suggestion on how the execution graph could be improved. The
 * optimization loop only generates suggestions (node order adjustment, agent
 * replacement, tool replacement); it never modifies the graph automatically.
 */
public record GraphOptimizationSuggestion(
		String type,
		String nodeId,
		String currentAgent,
		String recommendedAgent,
		String currentTool,
		String recommendedTool,
		String reason,
		double confidence) {

	public static final String ORDER = "ORDER";
	public static final String AGENT_REPLACEMENT = "AGENT_REPLACEMENT";
	public static final String TOOL_REPLACEMENT = "TOOL_REPLACEMENT";
}
