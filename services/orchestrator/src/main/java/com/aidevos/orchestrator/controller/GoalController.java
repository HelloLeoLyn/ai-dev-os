package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.goal.Goal;
import com.aidevos.orchestrator.goal.GoalEvaluation;
import com.aidevos.orchestrator.goal.GoalManagementService;
import com.aidevos.orchestrator.goal.GoalMilestone;
import com.aidevos.orchestrator.goal.GoalPriority;
import com.aidevos.orchestrator.goal.GoalTask;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autonomous goal management API: creates a goal, plans it (analyze ->
 * decompose -> generate tasks into the orchestrator pool) and reads the goal,
 * its milestones, its tasks and its progress evaluation. Errors are handled
 * by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/goals")
public class GoalController {

	private final GoalManagementService goalManagementService;

	public GoalController(GoalManagementService goalManagementService) {
		this.goalManagementService = goalManagementService;
	}

	@PostMapping
	public ResponseEntity<Goal> create(@RequestBody CreateGoalRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(goalManagementService.createGoal(request.projectId(), request.title(),
				request.description(), request.priority()));
	}

	@GetMapping("/{id}")
	public ResponseEntity<Goal> goal(@PathVariable String id) {
		return ResponseEntity.ok(goalManagementService.getGoal(id)
			.orElseThrow(() -> new ResourceNotFoundException("Goal", id)));
	}

	/**
	 * Plans the goal: analyze (memory + optimization), decompose into
	 * milestones and generate the tasks into the orchestrator task pool.
	 */
	@PostMapping("/{id}/plan")
	public ResponseEntity<Goal> plan(@PathVariable String id) {
		goalManagementService.analyzeGoal(id);
		goalManagementService.decomposeGoal(id);
		goalManagementService.generateTasks(id);
		return ResponseEntity.ok(goalManagementService.getGoal(id)
			.orElseThrow(() -> new ResourceNotFoundException("Goal", id)));
	}

	@GetMapping("/{id}/milestones")
	public ResponseEntity<List<GoalMilestone>> milestones(@PathVariable String id) {
		return ResponseEntity.ok(goalManagementService.getMilestones(id));
	}

	@GetMapping("/{id}/tasks")
	public ResponseEntity<List<GoalTask>> tasks(@PathVariable String id) {
		return ResponseEntity.ok(goalManagementService.getTasks(id));
	}

	@GetMapping("/{id}/progress")
	public ResponseEntity<GoalEvaluation> progress(@PathVariable String id) {
		return ResponseEntity.ok(goalManagementService.getEvaluation(id));
	}

	public record CreateGoalRequest(String projectId, String title, String description,
			GoalPriority priority) {
	}
}
