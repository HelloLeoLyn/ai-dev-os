package com.aidevos.orchestrator.job;

import java.util.List;
import com.aidevos.orchestrator.persistence.CrudRepository;

public interface JobRepository extends CrudRepository<ExecutionJob> {
	List<ExecutionJob> getByStatus(JobStatus status);
	/**
	 * Inserts the job only when no job with the same id exists and returns the
	 * stored job in both cases, making submission idempotent under concurrent
	 * schedulers.
	 */
	ExecutionJob createIfAbsent(ExecutionJob job);
}
