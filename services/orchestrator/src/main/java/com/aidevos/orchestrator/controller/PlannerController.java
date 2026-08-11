package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.orchestrator.OrchestrationTask;
import com.aidevos.orchestrator.orchestrator.TaskPriority;
import com.aidevos.orchestrator.planner.Plan;
import com.aidevos.orchestrator.planner.PlanningService;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dynamic planning API: generates an execution plan for a task (create,
 * evaluate, optimize) and reads plans by plan id or by task id. The plan is
 * the suggestion layer before the orchestrator converts it into an execution
 * graph; errors are handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/plans")
public class PlannerController {

	private final PlanningService planningService;
	private final TaskCenterService taskCenterService;

	public PlannerController(PlanningService planningService,
			TaskCenterService taskCenterService) {
		this.planningService = planningService;
		this.taskCenterService = taskCenterService;
	}

	/**
	 * Returns the plan by plan id, falling back to the latest plan of the
	 * task when the path variable is a task id.
	 */
	@GetMapping("/{id}")
	public ResponseEntity<Plan> plan(@PathVariable String id) {
		Plan plan = planningService.getPlan(id)
			.orElseGet(() -> planningService.getPlanByTaskId(id).orElse(null));
		if (plan == null) {
			throw new ResourceNotFoundException("Plan", id);
		}
		return ResponseEntity.ok(plan);
	}

	/**
	 * Generates the plan for a task: create -> evaluate -> optimize. The
	 * optional body carries the task type and priority used to shape the
	 * plan; the optimized plan is returned.
	 */
	@PostMapping("/{taskId}/generate")
	public ResponseEntity<Plan> generate(@PathVariable String taskId,
			@RequestBody(required = false) GenerateRequest request) {
		TaskRecord task = taskCenterService.getTask(taskId)
			.orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
		OrchestrationTask orchestrationTask = new OrchestrationTask(taskId,
			request == null ? null : request.taskType(),
			request == null ? null : request.priority(), List.of());
		Plan plan = planningService.createPlan(orchestrationTask);
		Plan evaluated = planningService.evaluatePlan(plan);
		return ResponseEntity.ok(planningService.optimizePlan(evaluated));
	}

	public record GenerateRequest(String taskType, TaskPriority priority) {
	}
}
