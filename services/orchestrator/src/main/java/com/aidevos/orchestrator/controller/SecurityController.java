package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.security.ApprovalRequest;
import com.aidevos.orchestrator.security.ApprovalService;
import com.aidevos.orchestrator.security.SecurityPolicy;
import com.aidevos.orchestrator.security.SecurityPolicyRegistry;
import com.aidevos.orchestrator.security.sandbox.SandboxContext;
import com.aidevos.orchestrator.security.sandbox.SandboxManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Security & sandbox API: policies, per-task sandbox state and the human
 * approval workflow for dangerous permissions.
 */
@RestController
@RequestMapping("/api")
public class SecurityController {

	private final SecurityPolicyRegistry policyRegistry;
	private final SandboxManager sandboxManager;
	private final ApprovalService approvalService;

	public SecurityController(SecurityPolicyRegistry policyRegistry,
			SandboxManager sandboxManager, ApprovalService approvalService) {
		this.policyRegistry = policyRegistry;
		this.sandboxManager = sandboxManager;
		this.approvalService = approvalService;
	}

	@GetMapping("/security/policies")
	public List<SecurityPolicy> policies() {
		return policyRegistry.listPolicies();
	}

	@GetMapping("/tasks/{taskId}/sandbox")
	public SandboxContext sandbox(@PathVariable String taskId) {
		return sandboxManager.getSandbox(taskId)
			.orElseThrow(() -> new ResourceNotFoundException("Sandbox", taskId));
	}

	@GetMapping("/security/approvals")
	public List<ApprovalRequest> approvals() {
		return approvalService.list();
	}

	@PostMapping("/security/approvals/{id}/approve")
	public ApprovalRequest approve(@PathVariable String id) {
		return approvalService.approve(id)
			.orElseThrow(() -> new ResourceNotFoundException("Approval", id));
	}

	@PostMapping("/security/approvals/{id}/reject")
	public ApprovalRequest reject(@PathVariable String id) {
		return approvalService.reject(id)
			.orElseThrow(() -> new ResourceNotFoundException("Approval", id));
	}
}
