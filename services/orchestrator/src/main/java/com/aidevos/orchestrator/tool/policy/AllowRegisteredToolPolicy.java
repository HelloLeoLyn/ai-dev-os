package com.aidevos.orchestrator.tool.policy;

import com.aidevos.orchestrator.tool.ToolDefinition;
import com.aidevos.orchestrator.tool.ToolInvocation;
import com.aidevos.orchestrator.tool.ToolAccess;
import org.springframework.stereotype.Component;

@Component
public class AllowRegisteredToolPolicy implements ToolPolicy {

	@Override
	public ToolPolicyDecision evaluate(ToolDefinition definition, ToolInvocation invocation) {
		return definition.access() == ToolAccess.READ_ONLY
			? ToolPolicyDecision.allow()
			: ToolPolicyDecision.requireApproval("Tool requests workspace-write permission");
	}
}
