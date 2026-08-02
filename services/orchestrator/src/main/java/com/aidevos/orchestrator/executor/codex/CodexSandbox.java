package com.aidevos.orchestrator.executor.codex;

public enum CodexSandbox {

	READ_ONLY("read-only"),
	WORKSPACE_WRITE("workspace-write");

	private final String cliValue;

	CodexSandbox(String cliValue) {
		this.cliValue = cliValue;
	}

	public String cliValue() {
		return cliValue;
	}

	public static CodexSandbox parse(String value) {
		if (value == null || value.isBlank()) {
			return WORKSPACE_WRITE;
		}
		for (CodexSandbox sandbox : values()) {
			if (sandbox.cliValue.equals(value)) {
				return sandbox;
			}
		}
		throw new IllegalArgumentException("Unsupported Codex sandbox: " + value);
	}
}
