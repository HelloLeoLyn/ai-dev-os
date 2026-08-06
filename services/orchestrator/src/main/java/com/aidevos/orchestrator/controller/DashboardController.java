package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.dashboard.DashboardQueryService;
import com.aidevos.orchestrator.dashboard.DashboardService;
import com.aidevos.orchestrator.dashboard.DashboardSummary;
import com.aidevos.orchestrator.dashboard.DashboardSummaryDTO;
import com.aidevos.orchestrator.dashboard.DashboardTimeline;
import com.aidevos.orchestrator.dashboard.ExecutionSummaryDTO;
import com.aidevos.orchestrator.dashboard.JobSummaryDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

	private final DashboardService dashboardService;
	private final DashboardQueryService dashboardQueryService;

	public DashboardController(DashboardService dashboardService,
			DashboardQueryService dashboardQueryService) {
		this.dashboardService = dashboardService;
		this.dashboardQueryService = dashboardQueryService;
	}

	@GetMapping
	public DashboardSummary getSummary() {
		return dashboardService.getSummary();
	}

	@GetMapping("/summary")
	public DashboardSummaryDTO getDashboardSummary() {
		return dashboardService.getDashboardSummary();
	}

	@GetMapping("/jobs")
	public List<JobSummaryDTO> getJobs() {
		return dashboardQueryService.listJobs();
	}

	@GetMapping("/executions")
	public List<ExecutionSummaryDTO> getExecutions() {
		return dashboardQueryService.listExecutions();
	}

	@GetMapping("/timeline/{id}")
	public DashboardTimeline getTimeline(@PathVariable String id) {
		return dashboardQueryService.timeline(id);
	}
}
