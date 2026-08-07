package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.job.JobService;
import com.aidevos.orchestrator.tool.approval.ToolApprovalRequest;
import com.aidevos.orchestrator.tool.approval.ToolApprovalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tool-approvals")
public class ToolApprovalController {

	private final ToolApprovalService approvalService;
	private final JobService jobService;

	public ToolApprovalController(ToolApprovalService approvalService, JobService jobService) {
		this.approvalService = approvalService;
		this.jobService = jobService;
	}

	@GetMapping
	public List<ToolApprovalRequest> getAll() {
		return approvalService.getAll();
	}

	@PostMapping("/{id}/approve")
	public ResponseEntity<ToolApprovalRequest> approve(@PathVariable String id) {
		ToolApprovalRequest request = approvalService.approve(id);
		if (request == null) {
			throw new ResourceNotFoundException("Tool approval request", id);
		}
		if (request.getJobId() != null && !jobService.resumeAfterApproval(request.getJobId())) {
			return ResponseEntity.status(409).body(request);
		}
		return ResponseEntity.ok(request);
	}

	@PostMapping("/{id}/reject")
	public ResponseEntity<ToolApprovalRequest> reject(@PathVariable String id) {
		ToolApprovalRequest request = approvalService.reject(id);
		if (request != null && request.getJobId() != null) {
			jobService.rejectApproval(request.getJobId());
		}
		if (request == null) {
			throw new ResourceNotFoundException("Tool approval request", id);
		}
		return ResponseEntity.ok(request);
	}
}
