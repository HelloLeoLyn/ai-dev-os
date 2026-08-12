package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.taskcenter.CreateTaskRequest;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.project.ProjectTaskService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

	private final TaskCenterService taskCenterService;
	private final ProjectTaskService projectTaskService;

	public TaskController(TaskCenterService taskCenterService, ProjectTaskService projectTaskService) {
		this.taskCenterService = taskCenterService;
		this.projectTaskService = projectTaskService;
	}

	@PostMapping
	public ResponseEntity<TaskRecord> create(@RequestBody CreateTaskRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(projectTaskService.createTask(request));
	}

	@GetMapping
	public List<TaskRecord> getAll() {
		return taskCenterService.listTasks();
	}

	@GetMapping("/{id}")
	public ResponseEntity<TaskRecord> get(@PathVariable String id) {
		return ResponseEntity.ok(taskCenterService.getTask(id)
			.orElseThrow(() -> new ResourceNotFoundException("Task", id)));
	}

	@PostMapping("/{id}/approve")
	public ResponseEntity<?> approve(@PathVariable String id,
			@RequestBody DecisionRequest request) {
		ensureTask(id);
		try {
			return ResponseEntity.ok(taskCenterService.approve(id, request.approver()));
		}
		catch (IllegalStateException exception) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
		}
	}

	@PostMapping("/{id}/reject")
	public ResponseEntity<?> reject(@PathVariable String id,
			@RequestBody DecisionRequest request) {
		ensureTask(id);
		try {
			return ResponseEntity.ok(taskCenterService.reject(id, request.approver(), request.reason()));
		}
		catch (IllegalStateException exception) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
		}
	}

	private void ensureTask(String id) {
		if (taskCenterService.getTask(id).isEmpty()) {
			throw new ResourceNotFoundException("Task", id);
		}
	}

	public record DecisionRequest(String approver, String reason) { }
}
