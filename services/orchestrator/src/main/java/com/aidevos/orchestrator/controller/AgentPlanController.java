package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.agentcoordinator.AgentCoordinatorService;
import com.aidevos.orchestrator.agentcoordinator.AgentExecutionPlan;
import com.aidevos.orchestrator.modelrouter.TaskType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent collaboration flow: create/run a plan for a Task Center task and view
 * the resulting agent steps.
 */
@RestController
@RequestMapping("/api/agent-plans")
public class AgentPlanController {

	private final AgentCoordinatorService coordinator;

	public AgentPlanController(AgentCoordinatorService coordinator) {
		this.coordinator = coordinator;
	}

	@PostMapping("/{taskId}")
	public ResponseEntity<List<AgentExecutionPlan>> create(@PathVariable String taskId,
			@RequestParam(required = false) String taskType) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(coordinator.createCollaborationPlan(taskId, TaskType.from(taskType)));
	}

	@GetMapping("/{taskId}")
	public ResponseEntity<List<AgentExecutionPlan>> get(@PathVariable String taskId) {
		return coordinator.getCollaborationPlan(taskId)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Void> handleIllegalArgument(IllegalArgumentException exception) {
		return ResponseEntity.badRequest().build();
	}
}
