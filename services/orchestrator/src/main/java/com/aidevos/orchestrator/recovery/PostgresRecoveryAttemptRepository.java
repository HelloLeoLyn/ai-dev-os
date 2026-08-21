package com.aidevos.orchestrator.recovery;

import java.util.List;

import com.aidevos.orchestrator.persistence.postgresql.PostgresDocumentStore;
import com.aidevos.orchestrator.recovery.RecoveryAttempt.AttemptStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * Postgres RecoveryAttempt 存储：复用 PostgresDocumentStore（type=recovery-attempt）。
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
final class PostgresRecoveryAttemptRepository implements RecoveryAttemptRepository {

	private static final String TYPE = "recovery-attempt";

	private final PostgresDocumentStore store;

	PostgresRecoveryAttemptRepository(PostgresDocumentStore store) {
		this.store = store;
	}

	@Override
	public void save(RecoveryAttempt attempt) {
		store.put(TYPE, attempt.attemptId(), attempt, "task:" + attempt.taskId());
	}

	@Override
	public List<RecoveryAttempt> findByFingerprint(String taskId, String fingerprint,
			RecoveryAction action) {
		return store.all(TYPE, RecoveryAttempt.class).stream()
			.filter(attempt -> taskId.equals(attempt.taskId())
				&& fingerprint.equals(attempt.fingerprint())
				&& attempt.action() == action
				&& attempt.status() != AttemptStatus.PENDING
				&& attempt.status() != AttemptStatus.RUNNING)
			.toList();
	}

	@Override
	public List<RecoveryAttempt> list() {
		return store.all(TYPE, RecoveryAttempt.class);
	}

	@Override
	public boolean hasRunning(String taskId, String fingerprint, RecoveryAction action) {
		return store.all(TYPE, RecoveryAttempt.class).stream()
			.anyMatch(attempt -> taskId.equals(attempt.taskId())
				&& fingerprint.equals(attempt.fingerprint())
				&& attempt.action() == action
				&& (attempt.status() == AttemptStatus.PENDING
					|| attempt.status() == AttemptStatus.RUNNING));
	}
}
