package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.pr.PullRequestCreateRequest;
import com.aidevos.orchestrator.pr.PullRequestRecord;
import com.aidevos.orchestrator.pr.PullRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pull request API: open a pull request for a pushed commit and manage its
 * state. Errors are handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api")
public class PullRequestController {

	private final PullRequestService pullRequestService;

	public PullRequestController(PullRequestService pullRequestService) {
		this.pullRequestService = pullRequestService;
	}

	@PostMapping("/commits/{id}/pull-request")
	public ResponseEntity<PullRequestRecord> create(@PathVariable String id,
			@RequestBody(required = false) PullRequestCreateRequest request) {
		return ResponseEntity.ok(pullRequestService.createPullRequest(id, request));
	}

	@GetMapping("/pull-requests/{id}")
	public ResponseEntity<PullRequestRecord> get(@PathVariable String id) {
		return ResponseEntity.ok(pullRequestService.get(id)
			.orElseThrow(() -> new ResourceNotFoundException("PullRequest", id)));
	}

	@PostMapping("/pull-requests/{id}/close")
	public ResponseEntity<PullRequestRecord> close(@PathVariable String id) {
		return ResponseEntity.ok(pullRequestService.close(id));
	}

	@PostMapping("/pull-requests/{id}/merge")
	public ResponseEntity<PullRequestRecord> merge(@PathVariable String id) {
		return ResponseEntity.ok(pullRequestService.merge(id));
	}
}
