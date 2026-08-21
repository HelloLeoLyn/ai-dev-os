package com.aidevos.orchestrator.recovery;

import java.util.List;

import com.aidevos.orchestrator.recovery.RecoveryAttempt.AttemptStatus;

/**
 * RecoveryAttempt 存储（budget 持久化：重启后不重置）。
 */
public interface RecoveryAttemptRepository {

	void save(RecoveryAttempt attempt);

	/** 同 taskId + fingerprint + action 的既有 attempt（自动 recovery 用）。 */
	List<RecoveryAttempt> findByFingerprint(String taskId, String fingerprint,
			RecoveryAction action);

	/** 同 taskId + fingerprint + action 是否有进行中的 attempt（递归/并发保护）。 */
	boolean hasRunning(String taskId, String fingerprint, RecoveryAction action);

	List<RecoveryAttempt> list();
}
