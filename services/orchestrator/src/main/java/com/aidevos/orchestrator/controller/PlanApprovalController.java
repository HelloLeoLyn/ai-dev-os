package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService.PlanApprovalNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plan-approvals")
public class PlanApprovalController {

	private final PlanApprovalService approvalService;

	public PlanApprovalController(PlanApprovalService approvalService) {
		this.approvalService = approvalService;
	}

	@GetMapping
	public List<PlanApprovalRequest> getAll() {
		return approvalService.getAll();
	}

	@GetMapping("/{id}")
	public ResponseEntity<PlanApprovalRequest> get(@PathVariable String id) {
		PlanApprovalRequest request = approvalService.get(id);
		return request == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(request);
	}

	@PostMapping
	public ResponseEntity<PlanApprovalRequest> create(@RequestBody CreateRequest body) {
		try {
			return ResponseEntity.ok(approvalService.create(body.requestId(), body.plan()));
		}
		catch (IllegalArgumentException | IllegalStateException exception) {
			return ResponseEntity.badRequest().build();
		}
	}

	@PostMapping("/{id}/approve")
	public ResponseEntity<PlanApprovalRequest> approve(@PathVariable String id,
			@RequestBody DecisionRequest body) {
		return decide(() -> approvalService.approve(id, body.approver()));
	}

	@PostMapping("/{id}/reject")
	public ResponseEntity<PlanApprovalRequest> reject(@PathVariable String id,
			@RequestBody DecisionRequest body) {
		return decide(() -> approvalService.reject(id, body.approver(), body.reason()));
	}

	private ResponseEntity<PlanApprovalRequest> decide(Decision operation) {
		try {
			return ResponseEntity.ok(operation.apply());
		}
		catch (PlanApprovalNotFoundException exception) {
			return ResponseEntity.notFound().build();
		}
		catch (IllegalArgumentException | IllegalStateException exception) {
			return ResponseEntity.status(409).build();
		}
	}

	public record CreateRequest(String requestId, Plan plan) { }

	public record DecisionRequest(String approver, String reason) { }

	@FunctionalInterface
	private interface Decision {
		PlanApprovalRequest apply();
	}
}
