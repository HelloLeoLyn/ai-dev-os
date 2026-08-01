package com.aidevos.orchestrator.openclaw.model;

public record OpenClawTaskResult(String runId, String sessionKey, String status, String output) {

	public boolean successful() {
		return "ok".equals(status);
	}
}
