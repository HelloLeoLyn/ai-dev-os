package com.aidevos.orchestrator.job;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.aidevos.orchestrator.model.TaskDefinition;
import org.springframework.stereotype.Service;

@Service
public class JobService {

	private final JobStore jobStore;
	private final JobWorker jobWorker;

	public JobService(JobStore jobStore, JobWorker jobWorker) {
		this.jobStore = jobStore;
		this.jobWorker = jobWorker;
	}

	public JobSubmissionResponse submit(TaskDefinition taskDefinition) {
		ExecutionJob job = new ExecutionJob(UUID.randomUUID().toString(), snapshot(taskDefinition));
		jobStore.save(job);
		if (!jobWorker.submit(job)) {
			jobStore.remove(job.getId());
			throw new JobQueueFullException();
		}
		return new JobSubmissionResponse(job.getId(), job.getTaskId(), JobStatus.QUEUED);
	}

	public ExecutionJob get(String id) {
		return jobStore.get(id);
	}

	public List<ExecutionJob> getAll(JobStatus status) {
		return status == null ? jobStore.getAll() : jobStore.getByStatus(status);
	}

	private TaskDefinition snapshot(TaskDefinition source) {
		TaskDefinition snapshot = new TaskDefinition();
		snapshot.setId(source.getId());
		snapshot.setName(source.getName());
		snapshot.setDescription(source.getDescription());
		snapshot.setAgentName(source.getAgentName());
		List<String> capabilities = source.getRequiredCapabilities();
		snapshot.setRequiredCapabilities(capabilities == null ? null : List.copyOf(capabilities));
		snapshot.setParameters(copyMap(source.getParameters()));
		snapshot.setStatus(source.getStatus());
		return snapshot;
	}

	private Map<String, Object> copyMap(Map<String, Object> source) {
		if (source == null) {
			return null;
		}
		Map<String, Object> copy = new LinkedHashMap<>();
		source.forEach((key, value) -> copy.put(key, copyValue(value)));
		return copy;
	}

	private Object copyValue(Object value) {
		if (value instanceof Map<?, ?> sourceMap) {
			Map<String, Object> copy = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
				if (entry.getKey() instanceof String key) {
					copy.put(key, copyValue(entry.getValue()));
				}
			}
			return copy;
		}
		if (value instanceof List<?> sourceList) {
			List<Object> copy = new ArrayList<>();
			sourceList.forEach(item -> copy.add(copyValue(item)));
			return copy;
		}
		return value;
	}
}
