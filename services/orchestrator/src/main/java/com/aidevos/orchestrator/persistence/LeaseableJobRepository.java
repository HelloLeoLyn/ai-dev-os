package com.aidevos.orchestrator.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobLease;
import com.aidevos.orchestrator.job.JobRepository;
import com.aidevos.orchestrator.job.JobStatus;

/**
 * Job repository extended with lease, fencing and recovery primitives.
 * Every lease-bound write must carry the latest owner and token; an update
 * affecting zero rows means the lease has been superseded and must be rejected.
 */
public interface LeaseableJobRepository extends JobRepository {

	Optional<JobLease> claimNext(Instant now, String owner, Duration leaseDuration);

	boolean renewLease(String jobId, String owner, long token, Instant newExpiry);

	boolean releaseLease(String jobId, String owner, long token, JobStatus nextStatus);

	boolean complete(String jobId, String owner, long token, ExecutionJob finalSnapshot);

	List<ExecutionJob> findStale(Instant now, int limit);

	boolean markRecoveryRequired(String jobId, String failureCode);

	boolean retryWait(String jobId, String failureCode, Instant availableAt);

	boolean cancel(String jobId);
}
