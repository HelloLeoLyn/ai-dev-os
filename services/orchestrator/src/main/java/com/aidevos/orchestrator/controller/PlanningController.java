package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.planner.PlannerService;
import com.aidevos.orchestrator.planner.PlanningRequest;
import com.aidevos.orchestrator.planner.PlanningResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planning")
public class PlanningController {

	private final PlannerService plannerService;

	public PlanningController(PlannerService plannerService) {
		this.plannerService = plannerService;
	}

	@PostMapping
	public ResponseEntity<PlanningResult> create(@RequestBody PlanningRequest request) {
		PlanningResult result = plannerService.createPlan(request);
		return result.success() ? ResponseEntity.ok(result)
			: ResponseEntity.unprocessableContent().body(result);
	}
}
