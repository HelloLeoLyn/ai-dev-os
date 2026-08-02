package com.aidevos.orchestrator.executor.codex;

public enum CodexApprovalPolicy {

	UNTRUSTED("untrusted"),
	ON_REQUEST("on-request"),
	NEVER("never");

	private final String cliValue;

	CodexApprovalPolicy(String cliValue) {
		this.cliValue = cliValue;
	}

	public String cliValue() {
		return cliValue;
	}
}
