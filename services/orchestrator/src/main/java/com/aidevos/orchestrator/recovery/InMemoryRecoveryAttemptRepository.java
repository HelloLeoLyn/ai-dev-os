package com.aidevos.orchestrator.recovery;

import java.util.ArrayList;
import java.util.List;

import com.aidevos.orchestrator.recovery.RecoveryAttempt.AttemptStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type",
	havingValue = "in-memory", matchIfMissing = true)
public class InMemoryRecoveryAttemptRepository implements RecoveryAttemptRepository {

	private final List<RecoveryAttempt> attempts = new ArrayList<>();

	@Override
	public synchronized void save(RecoveryAttempt attempt) {
		attempts.removeIf(existing -> existing.attemptId().equals(attempt.attemptId()));
		attempts.add(attempt);
	}

	@Override
	public synchronized List<RecoveryAttempt> findByFingerprint(String taskId,
			String fingerprint, RecoveryAction action) {
		List<RecoveryAttempt> result = new ArrayList<>();
		for (RecoveryAttempt attempt : attempts) {
			if (taskId.equals(attempt.taskId())
					&& fingerprint.equals(attempt.fingerprint())
					&& attempt.action() == action
					&& attempt.status() != AttemptStatus.PENDING
					&& attempt.status() != AttemptStatus.RUNNING) {
				result.add(attempt);
			}
		}
		return result;
	}

	@Override
	public synchronized List<RecoveryAttempt> list() {
		return List.copyOf(attempts);
	}

	@Override
	public synchronized boolean hasRunning(String taskId, String fingerprint,
			RecoveryAction action) {
		for (RecoveryAttempt attempt : attempts) {
			if (taskId.equals(attempt.taskId())
					&& fingerprint.equals(attempt.fingerprint())
					&& attempt.action() == action
					&& (attempt.status() == AttemptStatus.PENDING
						|| attempt.status() == AttemptStatus.RUNNING)) {
				return true;
			}
		}
		return false;
	}
}
