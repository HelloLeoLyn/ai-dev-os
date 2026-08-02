package com.aidevos.orchestrator.tool.policy;

public record ToolPolicyDecision(ToolPolicyAction action, String reason) {

	public static ToolPolicyDecision allow() {
		return new ToolPolicyDecision(ToolPolicyAction.ALLOW, "allowed");
	}

	public static ToolPolicyDecision deny(String reason) {
		return new ToolPolicyDecision(ToolPolicyAction.DENY, reason);
	}

	public static ToolPolicyDecision requireApproval(String reason) {
		return new ToolPolicyDecision(ToolPolicyAction.REQUIRE_APPROVAL, reason);
	}
}
