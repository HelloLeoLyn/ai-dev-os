package com.aidevos.orchestrator.metrics.agent;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent observability API: agent ranking, single agent detail and per-task
 * execution statistics. Errors are handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/metrics")
public class AgentMetricsController {

	private final AgentMetricsService agentMetricsService;

	public AgentMetricsController(AgentMetricsService agentMetricsService) {
		this.agentMetricsService = agentMetricsService;
	}

	@GetMapping("/agents")
	public List<AgentMetrics> agents() {
		return agentMetricsService.listAgentMetrics();
	}

	@GetMapping("/agents/{id}")
	public ResponseEntity<AgentMetricsDetail> agent(@PathVariable String id) {
		return ResponseEntity.ok(agentMetricsService.getAgentDetail(id));
	}

	@GetMapping("/tasks/{id}")
	public ResponseEntity<TaskExecutionMetrics> task(@PathVariable String id) {
		return ResponseEntity.ok(agentMetricsService.getTaskMetrics(id));
	}
}
