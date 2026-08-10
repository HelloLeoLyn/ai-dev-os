package com.aidevos.orchestrator.observability;

import java.util.List;

import com.aidevos.orchestrator.metrics.agent.TaskExecutionMetrics;
import com.aidevos.orchestrator.observability.usage.UsageSummary;
import com.aidevos.orchestrator.timeline.UnifiedTimeline;

/**
 * Task-level observability bundle: unified timeline, execution traces, agent
 * execution statistics, tool traces and token/cost usage.
 */
public record TaskObservability(
		String taskId,
		String taskStatus,
		UnifiedTimeline timeline,
		List<TraceRecord> traces,
		TaskExecutionMetrics agent,
		List<TraceRecord> toolTraces,
		UsageSummary usage) {

	public TaskObservability {
		traces = List.copyOf(traces);
		toolTraces = List.copyOf(toolTraces);
	}
}
