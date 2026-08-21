package com.aidevos.orchestrator.recovery;

/**
 * Recovery action scope（V1 不支持任意重新执行整个 Task）。
 */
public enum RecoveryAction {
	RETRY_STEP,
	RETRY_EXECUTION,
	RETRY_VALIDATION,
	RETRY_DELIVERY,
	REPLAN,
	HUMAN_INTERVENTION,
	ABORT
}
