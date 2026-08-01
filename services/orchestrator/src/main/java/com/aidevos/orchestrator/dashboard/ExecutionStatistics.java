package com.aidevos.orchestrator.dashboard;

public record ExecutionStatistics(long total, long successful, long failed,
		long unknown, double successRate) {
}
