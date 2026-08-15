package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.modelrouter.TaskType;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.task.TaskManager;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class ExecutionController {

	private final TaskManager taskManager;
	private final ExecutionEngine executionEngine;
	private final TaskCenterService taskCenterService;
	private final ExecutionWorkspaceService executionWorkspaceService;

	public ExecutionController(TaskManager taskManager, ExecutionEngine executionEngine) {
		this(taskManager, executionEngine, null);
	}

	public ExecutionController(TaskManager taskManager, ExecutionEngine executionEngine,
			TaskCenterService taskCenterService) {
		this(taskManager, executionEngine, taskCenterService, null);
	}

	@Autowired
	public ExecutionController(TaskManager taskManager, ExecutionEngine executionEngine,
			TaskCenterService taskCenterService, ExecutionWorkspaceService executionWorkspaceService) {
		this.taskManager = taskManager;
		this.executionEngine = executionEngine;
		this.taskCenterService = taskCenterService;
		this.executionWorkspaceService = executionWorkspaceService;
	}

	@PostMapping("/{id}/execute")
	public ResponseEntity<?> execute(@PathVariable String id,
			@RequestParam(required = false) String taskType) {
		if (taskCenterService != null && taskCenterService.getTask(id).isPresent()) {
			return ResponseEntity.ok(taskCenterService.execute(id, TaskType.from(taskType)));
		}
		TaskDefinition taskDefinition = taskManager.getTask(id);
		if (taskDefinition == null) {
			throw new ResourceNotFoundException("Task", id);
		}
		return ResponseEntity.ok(executionEngine.execute(taskDefinition));
	}

	@org.springframework.web.bind.annotation.GetMapping("/{id}/execution-workspace")
	public ResponseEntity<?> executionWorkspace(@PathVariable String id) {
		if (executionWorkspaceService == null) return ResponseEntity.notFound().build();
		var value = executionWorkspaceService.findByTaskId(id);
		return value == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(value);
	}
}
