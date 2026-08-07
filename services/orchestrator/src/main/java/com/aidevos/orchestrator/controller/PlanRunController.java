package com.aidevos.orchestrator.controller;

import java.net.URI;
import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.schedule.PlanScheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plan-runs")
public class PlanRunController {

	private final PlanScheduler scheduler;

	public PlanRunController(PlanScheduler scheduler) {
		this.scheduler = scheduler;
	}

	@PostMapping
	public ResponseEntity<PlanRun> start(@RequestBody StartRequest request) {
		try {
			PlanRun run = scheduler.start(request.approvalId());
			return ResponseEntity.accepted().location(URI.create("/api/plan-runs/" + run.getId()))
				.body(run);
		}
		catch (IllegalArgumentException exception) {
			throw new ResourceNotFoundException("Approval", request.approvalId());
		}
		catch (IllegalStateException exception) {
			return ResponseEntity.status(409).build();
		}
	}

	@GetMapping("/{id}")
	public ResponseEntity<PlanRun> get(@PathVariable String id) {
		PlanRun run = scheduler.get(id);
		if (run == null) {
			throw new ResourceNotFoundException("Plan run", id);
		}
		return ResponseEntity.ok(run);
	}

	@GetMapping
	public List<PlanRun> getAll() {
		return scheduler.getAll();
	}

	public record StartRequest(String approvalId) { }
}
