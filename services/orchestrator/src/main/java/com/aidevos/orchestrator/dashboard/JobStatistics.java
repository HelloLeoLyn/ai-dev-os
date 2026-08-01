package com.aidevos.orchestrator.dashboard;

public record JobStatistics(long total, long queued, long running, long succeeded,
		long failed, double successRate) {
}
