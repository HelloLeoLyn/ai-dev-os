package com.aidevos.orchestrator.job;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.aidevos.orchestrator.persistence.LeaseableJobRepository;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class JobStore implements LeaseableJobRepository {

	private final Map<String, ExecutionJob> jobs = new ConcurrentHashMap<>();

	public void save(ExecutionJob job) {
		jobs.put(job.getId(), job);
	}

	@Override
	public synchronized ExecutionJob createIfAbsent(ExecutionJob job) {
		ExecutionJob existing = jobs.putIfAbsent(job.getId(), job);
		return existing == null ? job : existing;
	}

	public ExecutionJob get(String id) {
		return jobs.get(id);
	}

	public void remove(String id) {
		jobs.remove(id);
	}

	public List<ExecutionJob> getAll() {
		List<ExecutionJob> result = new ArrayList<>(jobs.values());
		result.sort(Comparator.comparing(ExecutionJob::getCreatedAt)
			.thenComparing(ExecutionJob::getId));
		return result;
	}

	public List<ExecutionJob> getByStatus(JobStatus status) {
		return getAll().stream()
			.filter(job -> job.getStatus() == status)
			.toList();
	}

	@Override
	public synchronized Optional<JobLease> claimNext(Instant now, String owner,
			Duration leaseDuration) {
		ExecutionJob candidate = getAll().stream()
			.filter(job -> job.getStatus() == JobStatus.QUEUED
				|| job.getStatus() == JobStatus.RETRY_WAIT)
			.filter(job -> job.getAvailableAt() == null || !job.getAvailableAt().isAfter(now))
			.min(Comparator.comparing(ExecutionJob::getCreatedAt)
				.thenComparing(ExecutionJob::getId))
			.orElse(null);
		if (candidate == null) {
			return Optional.empty();
		}
		long token = candidate.getLeaseToken() == null ? 1 : candidate.getLeaseToken() + 1;
		Instant expiresAt = now.plus(leaseDuration);
		candidate.markRunning();
		candidate.nextAttemptNo();
		candidate.applyLease(new JobLease(owner, token, expiresAt));
		candidate.touchHeartbeat(now);
		candidate.bumpVersion();
		return Optional.of(new JobLease(owner, token, expiresAt));
	}

	@Override
	public synchronized boolean renewLease(String jobId, String owner, long token,
			Instant newExpiry) {
		ExecutionJob job = jobs.get(jobId);
		if (job == null || job.getStatus() != JobStatus.RUNNING || !hasLease(job, owner, token)) {
			return false;
		}
		job.applyLease(new JobLease(owner, token, newExpiry));
		job.touchHeartbeat(Instant.now());
		job.bumpVersion();
		return true;
	}

	@Override
	public synchronized boolean releaseLease(String jobId, String owner, long token,
			JobStatus nextStatus) {
		ExecutionJob job = jobs.get(jobId);
		if (job == null || job.getStatus() != JobStatus.RUNNING || !hasLease(job, owner, token)) {
			return false;
		}
		boolean transitioned = switch (nextStatus) {
			case QUEUED -> job.requeue();
			case CANCELLED -> job.markCancelled();
			default -> false;
		};
		if (!transitioned) {
			return false;
		}
		job.clearLease();
		job.bumpVersion();
		return true;
	}

	@Override
	public synchronized boolean complete(String jobId, String owner, long token,
			ExecutionJob finalSnapshot) {
		ExecutionJob stored = jobs.get(jobId);
		if (stored == null || !hasLease(stored, owner, token)) {
			return false;
		}
		JobStatus finalStatus = finalSnapshot.getStatus();
		if (finalStatus != JobStatus.SUCCESS && finalStatus != JobStatus.FAILED
				&& finalStatus != JobStatus.WAITING_APPROVAL) {
			return false;
		}
		stored.bumpVersion();
		stored.clearLease();
		return true;
	}

	@Override
	public List<ExecutionJob> findStale(Instant now, int limit) {
		return getAll().stream()
			.filter(job -> job.getStatus() == JobStatus.RUNNING)
			.filter(job -> job.getLeaseExpiresAt() != null && job.getLeaseExpiresAt().isBefore(now))
			.sorted(Comparator.comparing(ExecutionJob::getLeaseExpiresAt)
				.thenComparing(ExecutionJob::getId))
			.limit(limit)
			.toList();
	}

	@Override
	public synchronized boolean markRecoveryRequired(String jobId, String failureCode) {
		ExecutionJob job = jobs.get(jobId);
		if (job == null || !job.markRecoveryRequired(failureCode)) {
			return false;
		}
		job.clearLease();
		job.incrementRecoveryCount();
		job.bumpVersion();
		return true;
	}

	@Override
	public synchronized boolean retryWait(String jobId, String failureCode, Instant availableAt) {
		ExecutionJob job = jobs.get(jobId);
		if (job == null || !job.markRetryWait(failureCode, availableAt)) {
			return false;
		}
		job.clearLease();
		job.bumpVersion();
		return true;
	}

	@Override
	public synchronized boolean cancel(String jobId) {
		ExecutionJob job = jobs.get(jobId);
		if (job == null || !job.markCancelled()) {
			return false;
		}
		job.clearLease();
		job.bumpVersion();
		return true;
	}

	private boolean hasLease(ExecutionJob job, String owner, long token) {
		return job.getLeaseOwner() != null && job.getLeaseOwner().equals(owner)
			&& job.getLeaseToken() != null && job.getLeaseToken() == token;
	}
}
