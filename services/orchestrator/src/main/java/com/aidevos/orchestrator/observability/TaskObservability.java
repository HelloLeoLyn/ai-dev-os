package com.aidevos.orchestrator.observability;

import java.util.List;

import com.aidevos.orchestrator.collaboration.AgentMessage;
import com.aidevos.orchestrator.human.HumanApproval;
import com.aidevos.orchestrator.human.HumanFeedback;
import com.aidevos.orchestrator.metrics.agent.TaskExecutionMetrics;
import com.aidevos.orchestrator.observability.usage.UsageSummary;
import com.aidevos.orchestrator.optimization.OptimizationRecord;
import com.aidevos.orchestrator.runtime.AgentSession;
import com.aidevos.orchestrator.timeline.UnifiedTimeline;

/**
 * Task-level observability bundle: unified timeline, execution traces, agent
 * execution statistics, tool traces, runtime sessions, the agent
 * collaboration team (agents / messages / handoffs), human approvals and
 * feedback, optimization records and recommendation text, and token/cost
 * usage.
 */
public record TaskObservability(
		String taskId,
		String taskStatus,
		UnifiedTimeline timeline,
		List<TraceRecord> traces,
		TaskExecutionMetrics agent,
		List<TraceRecord> toolTraces,
		UsageSummary usage,
		List<AgentSession> sessions,
		String teamId,
		List<String> agents,
		List<AgentMessage> messages,
		List<String> handoffs,
		List<HumanApproval> approvals,
		List<HumanFeedback> humanFeedback,
		List<OptimizationRecord> optimizations,
		List<String> recommendations) {

	public TaskObservability {
		traces = List.copyOf(traces);
		toolTraces = List.copyOf(toolTraces);
		sessions = sessions == null ? List.of() : List.copyOf(sessions);
		agents = agents == null ? List.of() : List.copyOf(agents);
		messages = messages == null ? List.of() : List.copyOf(messages);
		handoffs = handoffs == null ? List.of() : List.copyOf(handoffs);
		approvals = approvals == null ? List.of() : List.copyOf(approvals);
		humanFeedback = humanFeedback == null ? List.of() : List.copyOf(humanFeedback);
		optimizations = optimizations == null ? List.of() : List.copyOf(optimizations);
		recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
	}
}
