package com.aidevos.orchestrator.job;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.outbox.OutboxTransactions;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class JobService {

	private final JobRepository jobStore;
	private final JobWorker jobWorker;
	private final AuditService auditService;
	private final OutboxTransactions outboxTransactions;

	public JobService(JobRepository jobStore, JobWorker jobWorker) {
		this(jobStore, jobWorker, AuditService.noop());
	}

	public JobService(JobRepository jobStore, JobWorker jobWorker, AuditService auditService) {
		this(jobStore, jobWorker, auditService, OutboxTransactions.passThrough());
	}

	@Autowired
	public JobService(JobRepository jobStore, JobWorker jobWorker, AuditService auditService,
			OutboxTransactions outboxTransactions) {
		this.jobStore = jobStore;
		this.jobWorker = jobWorker;
		this.auditService = auditService;
		this.outboxTransactions = outboxTransactions;
	}

	public JobSubmissionResponse submit(TaskDefinition taskDefinition) {
		return submit(taskDefinition, UUID.randomUUID().toString());
	}

	/**
	 * Idempotent submission: a caller-provided deterministic job id makes a
	 * retried submission return the previously created job instead of creating
	 * a duplicate. Used by the plan scheduler to keep attempt/job creation
	 * safe across scheduler restarts.
	 */
	public JobSubmissionResponse submit(TaskDefinition taskDefinition, String jobId) {
		if (jobId == null || jobId.isBlank()) {
			throw new IllegalArgumentException("Job id is required");
		}
		ExecutionJob job = new ExecutionJob(jobId, snapshot(taskDefinition));
		return outboxTransactions.execute(() -> {
			ExecutionJob stored = jobStore.createIfAbsent(job);
			if (stored != job) {
				return new JobSubmissionResponse(stored.getId(), stored.getTaskId(),
					stored.getStatus());
			}
			if (!jobWorker.submit(job)) {
				jobStore.remove(job.getId());
				throw new JobQueueFullException();
			}
			auditService.jobEvent(EventType.JOB_SUBMITTED, job, null, JobStatus.QUEUED.name());
			return new JobSubmissionResponse(job.getId(), job.getTaskId(), JobStatus.QUEUED);
		});
	}

	public ExecutionJob get(String id) {
		return jobStore.get(id);
	}

	public List<ExecutionJob> getAll(JobStatus status) {
		return status == null ? jobStore.getAll() : jobStore.getByStatus(status);
	}

	public boolean resumeAfterApproval(String jobId) {
		ExecutionJob job = jobStore.get(jobId);
		if (job == null || !job.resumeAfterApproval()) {
			return false;
		}
		return outboxTransactions.execute(() -> {
			if (jobWorker.submit(job)) {
				jobStore.save(job);
				auditService.jobEvent(EventType.JOB_RESUBMITTED, job,
					JobStatus.WAITING_APPROVAL.name(), JobStatus.QUEUED.name());
				return true;
			}
			job.restoreWaitingApproval();
			jobStore.save(job);
			return false;
		});
	}

	public boolean rejectApproval(String jobId) {
		ExecutionJob job = jobStore.get(jobId);
		if (job == null || !job.rejectApproval()) return false;
		return outboxTransactions.execute(() -> {
			jobStore.save(job);
			auditService.jobEvent(EventType.JOB_APPROVAL_REJECTED, job,
				JobStatus.WAITING_APPROVAL.name(), JobStatus.FAILED.name());
			return true;
		});
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
		snapshot.setMetadata(copyMap(source.getMetadata()));
		snapshot.setStatus(source.getStatus());
		snapshot.setOperation(source.getOperation());
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
