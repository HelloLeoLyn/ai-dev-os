package com.aidevos.orchestrator.execution;

import java.time.Instant;
import java.util.List;

public interface ExecutionAttemptRepository {

	void save(ExecutionAttempt attempt);

	ExecutionAttempt get(String id);

	List<ExecutionAttempt> getByJob(String jobId);

	List<ExecutionAttempt> listActive();

	List<ExecutionAttempt> findAbandoned(Instant now);
}
