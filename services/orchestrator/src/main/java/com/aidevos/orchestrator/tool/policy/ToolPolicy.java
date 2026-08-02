package com.aidevos.orchestrator.tool.policy;

import com.aidevos.orchestrator.tool.ToolDefinition;
import com.aidevos.orchestrator.tool.ToolInvocation;

public interface ToolPolicy {

	ToolPolicyDecision evaluate(ToolDefinition definition, ToolInvocation invocation);
}
