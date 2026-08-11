package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.optimization.AgentOptimizationService;
import com.aidevos.orchestrator.optimization.AgentScore;
import com.aidevos.orchestrator.optimization.OptimizationRecord;
import com.aidevos.orchestrator.optimization.OptimizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autonomous optimization API: runs the learning loop for a task (analyze),
 * lists the recorded recommendations and the ranked agent scores. Only
 * suggestions are produced; nothing is applied automatically. Errors are
 * handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/optimization")
public class OptimizationController {

	private final OptimizationService optimizationService;
	private final AgentOptimizationService agentOptimizationService;

	public OptimizationController(OptimizationService optimizationService,
			AgentOptimizationService agentOptimizationService) {
		this.optimizationService = optimizationService;
		this.agentOptimizationService = agentOptimizationService;
	}

	@GetMapping("/tasks/{taskId}")
	public ResponseEntity<List<OptimizationRecord>> taskRecommendations(
			@PathVariable String taskId) {
		return ResponseEntity.ok(optimizationService.getRecommendations(taskId));
	}

	@GetMapping("/agents")
	public ResponseEntity<List<AgentScore>> agents() {
		return ResponseEntity.ok(agentOptimizationService.scoreAllAgents());
	}

	@GetMapping("/recommendations")
	public ResponseEntity<List<OptimizationRecord>> recommendations() {
		return ResponseEntity.ok(optimizationService.getAllRecommendations());
	}

	@PostMapping("/tasks/{taskId}/analyze")
	public ResponseEntity<List<OptimizationRecord>> analyze(@PathVariable String taskId) {
		return ResponseEntity.ok(optimizationService.analyzeTask(taskId));
	}

	@GetMapping("/recommendations/{id}")
	public ResponseEntity<OptimizationRecord> recommendation(@PathVariable String id) {
		return ResponseEntity.ok(optimizationService.getRecommendation(id)
			.orElseThrow(() -> new ResourceNotFoundException(
				"Optimization recommendation", id)));
	}
}
