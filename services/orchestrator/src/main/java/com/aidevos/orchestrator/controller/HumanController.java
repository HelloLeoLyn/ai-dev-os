package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.human.HumanApproval;
import com.aidevos.orchestrator.human.HumanCollaborationService;
import com.aidevos.orchestrator.human.HumanFeedback;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Human collaboration API: lists and reviews approval requests and submits
 * feedback for agents. Approving a request resumes the paused runtime
 * session; errors are handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/human")
public class HumanController {

	private final HumanCollaborationService humanCollaborationService;

	public HumanController(HumanCollaborationService humanCollaborationService) {
		this.humanCollaborationService = humanCollaborationService;
	}

	@GetMapping("/tasks/{taskId}/approvals")
	public ResponseEntity<List<HumanApproval>> taskApprovals(@PathVariable String taskId) {
		return ResponseEntity.ok(humanCollaborationService.getTaskApprovals(taskId));
	}

	@GetMapping("/approvals/{id}")
	public ResponseEntity<HumanApproval> getApproval(@PathVariable String id) {
		return ResponseEntity.ok(humanCollaborationService.getApproval(id)
			.orElseThrow(() -> new ResourceNotFoundException("Human approval", id)));
	}

	@PostMapping("/approvals/{id}/approve")
	public ResponseEntity<HumanApproval> approve(@PathVariable String id,
			@RequestBody ReviewRequest request) {
		return ResponseEntity.ok(humanCollaborationService.approve(id,
			request.reviewer(), request.comment()));
	}

	@PostMapping("/approvals/{id}/reject")
	public ResponseEntity<HumanApproval> reject(@PathVariable String id,
			@RequestBody ReviewRequest request) {
		return ResponseEntity.ok(humanCollaborationService.reject(id,
			request.reviewer(), request.comment()));
	}

	@PostMapping("/tasks/{taskId}/feedback")
	public ResponseEntity<HumanFeedback> addFeedback(@PathVariable String taskId,
			@RequestBody FeedbackRequest request) {
		return ResponseEntity.ok(humanCollaborationService.addFeedback(taskId,
			request.sessionId(), request.agentType(), request.content()));
	}

	public record ReviewRequest(String reviewer, String comment) {
	}

	public record FeedbackRequest(String sessionId, String agentType, String content) {
	}
}
