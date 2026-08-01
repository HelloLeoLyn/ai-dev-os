package com.aidevos.orchestrator.executor.command.policy;

public record PolicyDecision(PolicyAction action, String ruleId) {

	public static PolicyDecision allow(String ruleId) {
		return new PolicyDecision(PolicyAction.ALLOW, ruleId);
	}

	public static PolicyDecision deny(String ruleId) {
		return new PolicyDecision(PolicyAction.DENY, ruleId);
	}

	public static PolicyDecision requireApproval(String ruleId) {
		return new PolicyDecision(PolicyAction.REQUIRE_APPROVAL, ruleId);
	}

	public boolean isAllowed() {
		return action == PolicyAction.ALLOW;
	}
}
