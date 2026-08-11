package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.metrics.tool.ToolMetrics;
import com.aidevos.orchestrator.metrics.tool.ToolMetricsService;
import com.aidevos.orchestrator.observability.AgentObservability;
import com.aidevos.orchestrator.observability.GoalObservability;
import com.aidevos.orchestrator.observability.ObservabilityService;
import com.aidevos.orchestrator.observability.ProjectObservability;
import com.aidevos.orchestrator.observability.TaskObservability;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Observability API: per-task trace/timeline/usage bundles, project and agent
 * aggregates plus tool statistics. Errors are handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {

	private final ObservabilityService observabilityService;
	private final ToolMetricsService toolMetricsService;

	public ObservabilityController(ObservabilityService observabilityService,
			ToolMetricsService toolMetricsService) {
		this.observabilityService = observabilityService;
		this.toolMetricsService = toolMetricsService;
	}

	@GetMapping("/tasks/{taskId}")
	public ResponseEntity<TaskObservability> task(@PathVariable String taskId) {
		return ResponseEntity.ok(observabilityService.taskObservability(taskId));
	}

	@GetMapping("/projects/{projectId}")
	public ResponseEntity<ProjectObservability> project(@PathVariable String projectId) {
		return ResponseEntity.ok(observabilityService.projectObservability(projectId));
	}

	@GetMapping("/goals/{goalId}")
	public ResponseEntity<GoalObservability> goal(@PathVariable String goalId) {
		return ResponseEntity.ok(observabilityService.goalObservability(goalId));
	}

	@GetMapping("/agents/{agentType}")
	public ResponseEntity<AgentObservability> agent(@PathVariable String agentType) {
		return ResponseEntity.ok(observabilityService.agentObservability(agentType));
	}

	@GetMapping("/tools")
	public List<ToolMetrics> tools() {
		return toolMetricsService.listToolMetrics();
	}
}
