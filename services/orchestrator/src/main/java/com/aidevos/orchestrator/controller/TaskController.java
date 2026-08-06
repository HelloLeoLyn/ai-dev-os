package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.taskcenter.CreateTaskRequest;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
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

	public TaskController(TaskCenterService taskCenterService) {
		this.taskCenterService = taskCenterService;
	}

	@PostMapping
	public ResponseEntity<TaskRecord> create(@RequestBody CreateTaskRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(taskCenterService.createTask(request));
	}

	@GetMapping
	public List<TaskRecord> getAll() {
		return taskCenterService.listTasks();
	}

	@GetMapping("/{id}")
	public ResponseEntity<TaskRecord> get(@PathVariable String id) {
		return taskCenterService.getTask(id)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
