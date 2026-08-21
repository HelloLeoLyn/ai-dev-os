package com.aidevos.orchestrator.diagnosis;

import java.time.Instant;
import java.util.List;

/**
 * Known Failure：以 fingerprint 为身份的历史失败记录。
 *
 * 同一 fingerprint 同类错误只存在一条记录；occurrenceCount 按
 * fingerprint + taskId 去重计数（同一 Task 重复诊断不增加计数）。
 * V1 不存整份原始日志。
 */
public record KnownFailure(
	String fingerprint,
	String code,
	FailureCategory category,
	String rootCause,
	RecommendedAction recommendedAction,
	Instant firstSeenAt,
	Instant lastSeenAt,
	long occurrenceCount,
	String exampleTaskId,
	/** 已计入计数的 task id（幂等：同 task 同 fingerprint 只记一次） */
	List<String> seenTaskIds
) {

	public KnownFailure {
		seenTaskIds = seenTaskIds == null ? List.of() : List.copyOf(seenTaskIds);
	}

	public static KnownFailure first(String fingerprint, String code, FailureCategory category,
			String rootCause, RecommendedAction recommendedAction, String taskId, Instant now) {
		return new KnownFailure(fingerprint, code, category, rootCause, recommendedAction,
			now, now, 1, taskId, List.of(taskId));
	}

	/** 新 task 计入：count+1、lastSeenAt 更新、exampleTaskId 保留首个。 */
	public KnownFailure withOccurrence(String taskId, Instant now) {
		java.util.ArrayList<String> seen = new java.util.ArrayList<>(seenTaskIds);
		seen.add(taskId);
		return new KnownFailure(fingerprint, code, category, rootCause, recommendedAction,
			firstSeenAt, now, seen.size(), exampleTaskId, List.copyOf(seen));
	}
}
