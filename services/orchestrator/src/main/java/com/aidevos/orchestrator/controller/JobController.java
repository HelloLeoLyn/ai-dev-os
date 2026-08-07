package com.aidevos.orchestrator.controller;

import java.net.URI;
import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobQueueFullException;
import com.aidevos.orchestrator.job.JobService;
import com.aidevos.orchestrator.job.JobStatus;
import com.aidevos.orchestrator.job.JobSubmissionResponse;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.task.TaskManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JobController {

	private final TaskManager taskManager;
	private final JobService jobService;

	public JobController(TaskManager taskManager, JobService jobService) {
		this.taskManager = taskManager;
		this.jobService = jobService;
	}

	@PostMapping("/api/tasks/{id}/jobs")
	public ResponseEntity<JobSubmissionResponse> submit(@PathVariable String id) {
		TaskDefinition taskDefinition = taskManager.getTask(id);
		if (taskDefinition == null) {
			throw new ResourceNotFoundException("Task", id);
		}
		try {
			JobSubmissionResponse response = jobService.submit(taskDefinition);
			return ResponseEntity.accepted()
				.location(URI.create("/api/jobs/" + response.jobId()))
				.body(response);
		}
		catch (JobQueueFullException ex) {
			return ResponseEntity.status(429).build();
		}
	}

	@GetMapping("/api/jobs/{id}")
	public ResponseEntity<ExecutionJob> get(@PathVariable String id) {
		ExecutionJob job = jobService.get(id);
		if (job == null) {
			throw new ResourceNotFoundException("Job", id);
		}
		return ResponseEntity.ok(job);
	}

	@GetMapping("/api/jobs")
	public List<ExecutionJob> getAll(@RequestParam(required = false) JobStatus status) {
		return jobService.getAll(status);
	}
}
