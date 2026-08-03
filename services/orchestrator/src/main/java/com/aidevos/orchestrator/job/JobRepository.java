package com.aidevos.orchestrator.job;

import java.util.List;
import com.aidevos.orchestrator.persistence.CrudRepository;

public interface JobRepository extends CrudRepository<ExecutionJob> {
	List<ExecutionJob> getByStatus(JobStatus status);
}
