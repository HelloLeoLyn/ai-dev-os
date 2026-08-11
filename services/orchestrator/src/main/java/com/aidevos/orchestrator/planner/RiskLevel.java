package com.aidevos.orchestrator.planner;

/**
 * Risk level of an execution plan. The dynamic planner derives it from the
 * task category, the memory context (warnings) and the optimization
 * recommendations; a higher risk raises the evaluation penalty and may add a
 * repair step during optimization.
 */
public enum RiskLevel {
	LOW,
	MEDIUM,
	HIGH,
	CRITICAL
}
