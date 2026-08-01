package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.dashboard.DashboardService;
import com.aidevos.orchestrator.dashboard.DashboardSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

	private final DashboardService dashboardService;

	public DashboardController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	@GetMapping
	public DashboardSummary getSummary() {
		return dashboardService.getSummary();
	}
}
