package com.aidevos.orchestrator.dashboard;

import java.time.Instant;
import java.util.List;

public record DashboardSummary(Instant generatedAt, TaskStatistics tasks,
		JobStatistics jobs, ExecutionStatistics executions,
		List<RecentJobSummary> recentJobs) {

	public DashboardSummary {
		recentJobs = List.copyOf(recentJobs);
	}
}
