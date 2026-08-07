package com.aidevos.orchestrator.metrics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operational metrics endpoint. Returns current registry and repository
 * counts for long-running monitoring.
 */
@RestController
public class MetricsController {

	private final MetricsService metricsService;

	public MetricsController(MetricsService metricsService) {
		this.metricsService = metricsService;
	}

	@GetMapping("/api/metrics")
	public MetricsSnapshot metrics() {
		return metricsService.collect();
	}
}
