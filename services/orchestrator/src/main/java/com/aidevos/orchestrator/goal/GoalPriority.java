package com.aidevos.orchestrator.goal;

/**
 * Priority of an autonomous goal; the generated tasks inherit the priority so
 * the orchestrator queue schedules them accordingly.
 */
public enum GoalPriority {
	LOW,
	NORMAL,
	HIGH,
	CRITICAL
}
