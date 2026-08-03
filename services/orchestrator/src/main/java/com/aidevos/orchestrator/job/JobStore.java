package com.aidevos.orchestrator.job;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class JobStore implements JobRepository {

	private final Map<String, ExecutionJob> jobs = new ConcurrentHashMap<>();

	public void save(ExecutionJob job) {
		jobs.put(job.getId(), job);
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
}
