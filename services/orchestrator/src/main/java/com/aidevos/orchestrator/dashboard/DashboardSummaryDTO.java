package com.aidevos.orchestrator.dashboard;

/**
 * Dashboard summary payload for the Phase 9-A-1 dashboard. Composed from
 * existing health, agent, job, execution and recovery data without changing
 * the orchestrator execution flow.
 */
public record DashboardSummaryDTO(
		Health health,
		Agents agents,
		JobStatistics jobs,
		ExecutionStatistics executions,
		Recovery recovery) {

	public record Health(String status, boolean ready) {
	}

	public record Agents(int total, int enabled) {
	}

	public record Recovery(int pending) {
	}
}
