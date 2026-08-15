package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.approval.CodingApprovalRequest;
import com.aidevos.orchestrator.approval.CodingApprovalService;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.job.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

	private final CodingApprovalService approvalService;
	private final JobService jobService;

	public ApprovalController(CodingApprovalService approvalService, JobService jobService) {
		this.approvalService = approvalService;
		this.jobService = jobService;
	}

	@GetMapping
	public List<CodingApprovalRequest> getAll() {
		return approvalService.getAll();
	}

	@GetMapping("/{id}")
	public ResponseEntity<CodingApprovalRequest> get(@PathVariable String id) {
		CodingApprovalRequest request = approvalService.get(id);
		if (request == null) {
			throw new ResourceNotFoundException("Approval request", id);
		}
		return ResponseEntity.ok(request);
	}

	@PostMapping("/{id}/approve")
	public ResponseEntity<CodingApprovalRequest> approve(@PathVariable String id) {
		CodingApprovalRequest request = approvalService.approve(id);
		if (request == null) {
			throw new ResourceNotFoundException("Approval request", id);
		}
		if (request.getJobId() != null) {
			if (!jobService.resumeAfterApproval(request.getJobId())) {
				return ResponseEntity.status(409).body(request);
			}
		}
		return ResponseEntity.ok(request);
	}

	@PostMapping("/{id}/reject")
	public ResponseEntity<CodingApprovalRequest> reject(@PathVariable String id) {
		CodingApprovalRequest request = approvalService.reject(id);
		if (request != null && request.getJobId() != null) {
			jobService.rejectApproval(request.getJobId());
		}
		if (request == null) {
			throw new ResourceNotFoundException("Approval request", id);
		}
		return ResponseEntity.ok(request);
	}
}
