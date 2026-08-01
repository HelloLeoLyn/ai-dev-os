package com.aidevos.orchestrator.dashboard;

import java.util.Map;

public record TaskStatistics(long total, Map<String, Long> byStatus) {

	public TaskStatistics {
		byStatus = Map.copyOf(byStatus);
	}
}
