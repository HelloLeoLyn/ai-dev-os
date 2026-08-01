package com.aidevos.orchestrator.job;

import java.util.List;
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
		snapshot.setStatus(source.getStatus());
		return snapshot;
	}
}
