package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.task.TaskManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

	private final TaskManager taskManager;

	public TaskController(TaskManager taskManager) {
		this.taskManager = taskManager;
	}

	@PostMapping
	public TaskDefinition register(@RequestBody TaskDefinition taskDefinition) {
		taskManager.register(taskDefinition);
		return taskDefinition;
	}

	@GetMapping
	public List<TaskDefinition> getAllTasks() {
		return taskManager.getAllTasks();
	}
}
