package com.aidevos.orchestrator.feedback;

/**
 * Lifecycle of a pull request feedback loop:
 * CREATED -> REPAIRING -> WAITING_REVIEW -> PUSHED -> RECHECKING -> SUCCESS.
 * FAILED is reached when the repair, commit or push fails, or the re-check
 * does not recover; retry returns a FAILED feedback to REPAIRING. The loop
 * never bypasses human review: WAITING_REVIEW only leaves after the ChangeSet
 * is approved.
 */
public enum FeedbackStatus {
	CREATED,
	REPAIRING,
	WAITING_REVIEW,
	PUSHED,
	RECHECKING,
	SUCCESS,
	FAILED
}
