package com.aidevos.orchestrator.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.execution.RunExecutionState;
import com.aidevos.orchestrator.human.HumanApproval;
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

	@GetMapping("/{id}/execution-state")
	public ResponseEntity<RunExecutionState> executionState(@PathVariable String id) {
		RunExecutionState state = scheduler.executionState(id);
		if (state == null) {
			throw new ResourceNotFoundException("Plan run execution state", id);
		}
		return ResponseEntity.ok(state);
	}

	@PostMapping("/{id}/intervention")
	public ResponseEntity<?> intervene(@PathVariable String id,
			@RequestBody InterventionRequest request) {
		try {
			return ResponseEntity.ok(scheduler.decideIntervention(id, request.action(),
				request.comment()));
		}
		catch (IllegalArgumentException exception) {
			throw new ResourceNotFoundException("Plan run", id);
		}
		catch (IllegalStateException exception) {
			return ResponseEntity.status(409).body(Map.of("message", exception.getMessage()));
		}
	}

	@GetMapping
	public List<PlanRun> getAll() {
		return scheduler.getAll();
	}

	@PostMapping("/{runId}/steps/{stepId}/approve")
	public ResponseEntity<HumanApproval> approveGate(@PathVariable String runId,
			@PathVariable String stepId, @RequestBody GateDecisionRequest request) {
		try {
			return ResponseEntity.ok(scheduler.approveHumanGate(runId, stepId,
				request.reviewer(), request.comment()));
		}
		catch (IllegalArgumentException exception) {
			throw new ResourceNotFoundException("Plan run step", runId + "/" + stepId);
		}
		catch (IllegalStateException exception) {
			return ResponseEntity.status(409).build();
		}
	}

	@PostMapping("/{runId}/steps/{stepId}/reject")
	public ResponseEntity<HumanApproval> rejectGate(@PathVariable String runId,
			@PathVariable String stepId, @RequestBody GateDecisionRequest request) {
		try {
			return ResponseEntity.ok(scheduler.rejectHumanGate(runId, stepId,
				request.reviewer(), request.comment()));
		}
		catch (IllegalArgumentException exception) {
			throw new ResourceNotFoundException("Plan run step", runId + "/" + stepId);
		}
		catch (IllegalStateException exception) {
			return ResponseEntity.status(409).build();
		}
	}

	public record StartRequest(String approvalId) { }

	public record GateDecisionRequest(String reviewer, String comment) { }

	public record InterventionRequest(String action, String comment) { }
}
