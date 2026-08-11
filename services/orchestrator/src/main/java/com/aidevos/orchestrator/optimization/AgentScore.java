package com.aidevos.orchestrator.optimization;

/**
 * Composite score of one agent for autonomous selection: execution statistics
 * derived from the existing AgentMetrics plus the collaboration and human
 * approval figures derived from the collaboration and human stores. All rates
 * are percentages in [0, 100]; avgDuration is in milliseconds.
 */
public record AgentScore(
		String agentType,
		int totalExecutions,
		double successRate,
		long avgDuration,
		double failureRate,
		double collaborationScore,
		double humanApprovalRate) {

	/**
	 * Weighted composite used for ranking. Higher is better: success and
	 * collaboration/approval contributions minus the failure rate.
	 */
	public double composite() {
		return successRate + collaborationScore * 0.5
			+ humanApprovalRate * 0.5 - failureRate;
	}
}
