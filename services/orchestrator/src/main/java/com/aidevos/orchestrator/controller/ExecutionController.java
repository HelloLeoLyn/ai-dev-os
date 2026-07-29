package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.task.TaskManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class ExecutionController {

	private final TaskManager taskManager;
	private final ExecutionEngine executionEngine;

	public ExecutionController(TaskManager taskManager, ExecutionEngine executionEngine) {
		this.taskManager = taskManager;
		this.executionEngine = executionEngine;
	}

	@PostMapping("/{id}/execute")
	public ResponseEntity<ExecutionResult> execute(@PathVariable String id) {
		TaskDefinition taskDefinition = taskManager.getTask(id);
		if (taskDefinition == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(executionEngine.execute(taskDefinition));
	}
}
