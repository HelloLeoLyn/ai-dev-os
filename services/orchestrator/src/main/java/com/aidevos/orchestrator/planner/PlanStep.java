package com.aidevos.orchestrator.planner;

import java.util.List;

import com.aidevos.orchestrator.agent.AgentType;

/**
 * One step of an execution plan: the agent that should run it, the tools it
 * may use and the steps it depends on. The step ids become the execution
 * graph node ids when the plan is converted to a graph.
 */
public record PlanStep(String stepId, String description, AgentType agentType,
		List<String> tools, List<String> dependencies) {

	public PlanStep {
		tools = tools == null ? List.of() : List.copyOf(tools);
		dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
	}

	public PlanStep withAgentType(AgentType replacement) {
		return new PlanStep(stepId, description, replacement, tools, dependencies);
	}

	public PlanStep withTools(List<String> newTools) {
		return new PlanStep(stepId, description, agentType, newTools, dependencies);
	}
}
