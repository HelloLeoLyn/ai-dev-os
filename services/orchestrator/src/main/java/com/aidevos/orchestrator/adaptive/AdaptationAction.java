package com.aidevos.orchestrator.adaptive;

/**
 * The actions the adaptive executor can take after a node failure: retry the
 * same node, switch the node's agent, modify the graph (insert a repair
 * step), change the tools available to the agent or replan the whole task
 * through the dynamic planner. Only the plan/graph of the running task is
 * affected; the Scheduler / Worker / ExecutionEngine are untouched.
 */
public enum AdaptationAction {
	RETRY,
	SWITCH_AGENT,
	MODIFY_GRAPH,
	CHANGE_TOOL,
	REPLAN
}
