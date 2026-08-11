package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.orchestrator.OrchestrationTask;
import com.aidevos.orchestrator.orchestrator.OrchestratorService;
import com.aidevos.orchestrator.orchestrator.TaskPool;
import com.aidevos.orchestrator.orchestrator.TaskPriority;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autonomous orchestrator API: submits tasks to the pool, starts them
 * (auto-assigning agents and planning the dynamic graph) and reads the pool
 * and queue state. Errors are handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/orchestrator")
public class OrchestratorController {

	private final OrchestratorService orchestratorService;

	public OrchestratorController(OrchestratorService orchestratorService) {
		this.orchestratorService = orchestratorService;
	}

	@GetMapping("/tasks")
	public ResponseEntity<List<OrchestrationTask>> tasks() {
		return ResponseEntity.ok(orchestratorService.listTasks());
	}

	@GetMapping("/tasks/{id}")
	public ResponseEntity<OrchestrationTask> task(@PathVariable String id) {
		return ResponseEntity.ok(orchestratorService.getTask(id)
			.orElseThrow(() -> new ResourceNotFoundException("Orchestration task", id)));
	}

	@PostMapping("/tasks/{id}/submit")
	public ResponseEntity<OrchestrationTask> submit(@PathVariable String id,
			@RequestBody(required = false) SubmitRequest request) {
		return ResponseEntity.ok(orchestratorService.submitTask(id,
			request == null ? null : request.taskType(),
			request == null ? null : request.priority(),
			request == null ? null : request.requiredAgents()));
	}

	@PostMapping("/tasks/{id}/start")
	public ResponseEntity<OrchestrationTask> start(@PathVariable String id) {
		return ResponseEntity.ok(orchestratorService.startTask(id));
	}

	@GetMapping("/pool")
	public ResponseEntity<TaskPool> pool() {
		return ResponseEntity.ok(orchestratorService.getPool());
	}

	public record SubmitRequest(String taskType, TaskPriority priority,
			List<String> requiredAgents) {
	}
}
