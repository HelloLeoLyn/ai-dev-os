package com.aidevos.orchestrator.optimization;

/**
 * Category of an autonomous optimization recommendation: which agent to
 * select, how to change the execution graph flow, how tools are used, which
 * failure patterns recur or where performance can be improved.
 */
public enum OptimizationType {
	AGENT_SELECTION,
	GRAPH_FLOW,
	TOOL_USAGE,
	FAILURE_PATTERN,
	PERFORMANCE
}
