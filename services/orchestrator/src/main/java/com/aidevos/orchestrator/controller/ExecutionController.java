package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.modelrouter.TaskType;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.task.TaskManager;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspaceService;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspacePromotionService;
import com.aidevos.orchestrator.execution.workspace.PromotionException;
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
	private final ExecutionWorkspacePromotionService promotionService;

	public ExecutionController(TaskManager taskManager, ExecutionEngine executionEngine) {
		this(taskManager, executionEngine, null, null, null);
	}

	public ExecutionController(TaskManager taskManager, ExecutionEngine executionEngine,
			TaskCenterService taskCenterService) {
		this(taskManager, executionEngine, taskCenterService, null, null);
	}

	@Autowired
	public ExecutionController(TaskManager taskManager, ExecutionEngine executionEngine,
			TaskCenterService taskCenterService, ExecutionWorkspaceService executionWorkspaceService,
			ExecutionWorkspacePromotionService promotionService) {
		this.taskManager = taskManager;
		this.executionEngine = executionEngine;
		this.taskCenterService = taskCenterService;
		this.executionWorkspaceService = executionWorkspaceService;
		this.promotionService = promotionService;
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

	@org.springframework.web.bind.annotation.GetMapping("/{id}/execution-workspace/review")
	public ResponseEntity<?> reviewExecutionWorkspace(@PathVariable String id) {
		if (promotionService == null) return ResponseEntity.notFound().build();
		return ResponseEntity.ok(promotionService.review(id));
	}

	@org.springframework.web.bind.annotation.PostMapping("/{id}/execution-workspace/promote")
	public ResponseEntity<?> promoteExecutionWorkspace(@PathVariable String id) {
		if (promotionService == null) return ResponseEntity.notFound().build();
		try { return ResponseEntity.ok(promotionService.promote(id)); }
		catch (PromotionException ex) { return ResponseEntity.status(409).body(java.util.Map.of("errorCode", ex.getErrorCode(), "message", ex.getMessage())); }
	}

	@org.springframework.web.bind.annotation.PostMapping("/{id}/execution-workspace/reject")
	public ResponseEntity<?> rejectExecutionWorkspace(@PathVariable String id) {
		if (promotionService == null) return ResponseEntity.notFound().build();
		try { return ResponseEntity.ok(promotionService.reject(id)); }
		catch (PromotionException ex) { return ResponseEntity.status(409).body(java.util.Map.of("errorCode", ex.getErrorCode(), "message", ex.getMessage())); }
	}
}
