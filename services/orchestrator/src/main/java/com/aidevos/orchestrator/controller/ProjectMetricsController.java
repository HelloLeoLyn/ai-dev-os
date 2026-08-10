package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.metrics.agent.AgentMetrics;
import com.aidevos.orchestrator.metrics.agent.AgentMetricsService;
import com.aidevos.orchestrator.project.ProjectService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project-scoped metrics: agent execution statistics restricted to one
 * project.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectMetricsController {

	private final ProjectService projectService;
	private final AgentMetricsService agentMetricsService;

	public ProjectMetricsController(ProjectService projectService,
			AgentMetricsService agentMetricsService) {
		this.projectService = projectService;
		this.agentMetricsService = agentMetricsService;
	}

	@GetMapping("/{id}/metrics")
	public List<AgentMetrics> metrics(@PathVariable String id) {
		projectService.getProject(id)
			.orElseThrow(() -> new ResourceNotFoundException("Project", id));
		return agentMetricsService.listProjectAgentMetrics(id);
	}
}
