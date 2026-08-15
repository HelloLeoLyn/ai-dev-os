package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.analysis.AnalysisInsightService;
import com.aidevos.orchestrator.analysis.AnalysisInsightSet;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks/{taskId}/analysis-insights")
public class AnalysisInsightController {
	private final AnalysisInsightService service;
	public AnalysisInsightController(AnalysisInsightService service) { this.service=service; }
	@GetMapping public ResponseEntity<AnalysisInsightResponse> get(@PathVariable String taskId) {
		AnalysisInsightSet insight=service.getByTaskId(taskId);
		return ResponseEntity.ok(new AnalysisInsightResponse(insight==null ? "NOT_GENERATED"
			: insight.status().name(), insight));
	}
	@PostMapping("/retry") public ResponseEntity<AnalysisInsightResponse> retry(@PathVariable String taskId) {
		AnalysisInsightSet insight=service.retry(taskId);
		return ResponseEntity.ok(new AnalysisInsightResponse(insight.status().name(), insight));
	}
	public record AnalysisInsightResponse(String status, AnalysisInsightSet insight) { }
}
