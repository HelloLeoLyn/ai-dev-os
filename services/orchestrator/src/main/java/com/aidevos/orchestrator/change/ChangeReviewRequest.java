package com.aidevos.orchestrator.change;

/**
 * Optional reviewer identity for approve/reject actions. When absent the
 * change is recorded as reviewed by SYSTEM.
 */
public record ChangeReviewRequest(String reviewer) {
}
