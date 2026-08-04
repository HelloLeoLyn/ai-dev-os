package com.aidevos.orchestrator.execution;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryExecutionAttemptRepository implements ExecutionAttemptRepository {

	private final Map<String, ExecutionAttempt> attempts = new ConcurrentHashMap<>();

	@Override
	public synchronized void save(ExecutionAttempt attempt) {
		attempts.put(attempt.getId(), attempt);
	}

	@Override
	public ExecutionAttempt get(String id) {
		return attempts.get(id);
	}

	@Override
	public List<ExecutionAttempt> getByJob(String jobId) {
		return attempts.values().stream()
			.filter(attempt -> attempt.getJobId().equals(jobId))
			.sorted(Comparator.comparingInt(ExecutionAttempt::getAttemptNo)
				.thenComparing(ExecutionAttempt::getId))
			.toList();
	}

	@Override
	public List<ExecutionAttempt> listActive() {
		return attempts.values().stream()
			.filter(attempt -> attempt.getStatus() == ExecutionAttemptStatus.STARTING
				|| attempt.getStatus() == ExecutionAttemptStatus.RUNNING)
			.sorted(Comparator.comparing(ExecutionAttempt::getCreatedAt)
				.thenComparing(ExecutionAttempt::getId))
			.toList();
	}

	@Override
	public List<ExecutionAttempt> findAbandoned(Instant now) {
		return attempts.values().stream()
			.filter(attempt -> attempt.getStatus() == ExecutionAttemptStatus.RUNNING)
			.filter(attempt -> attempt.getLeaseExpiresAt() != null
				&& attempt.getLeaseExpiresAt().isBefore(now))
			.sorted(Comparator.comparing(ExecutionAttempt::getCreatedAt)
				.thenComparing(ExecutionAttempt::getId))
			.toList();
	}
}
