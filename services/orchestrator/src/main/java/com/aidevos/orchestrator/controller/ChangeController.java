package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.change.ChangeReviewRequest;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Change management API: inspect AI change sets per task, read the raw git
 * diff and drive the review lifecycle (review / approve / reject). Errors are
 * handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api")
public class ChangeController {

	private final ChangeService changeService;

	public ChangeController(ChangeService changeService) {
		this.changeService = changeService;
	}

	@GetMapping("/tasks/{taskId}/changes")
	public List<ChangeSet> listByTask(@PathVariable String taskId) {
		return changeService.getChangesByTask(taskId);
	}

	@GetMapping("/changes/{id}")
	public ResponseEntity<ChangeSet> get(@PathVariable String id) {
		return ResponseEntity.ok(changeService.getChange(id)
			.orElseThrow(() -> new ResourceNotFoundException("Change", id)));
	}

	@GetMapping("/changes/{id}/diff")
	public ResponseEntity<String> diff(@PathVariable String id) {
		return ResponseEntity.ok()
			.contentType(MediaType.TEXT_PLAIN)
			.body(changeService.getDiff(id));
	}

	@PostMapping("/changes/{id}/review")
	public ResponseEntity<ChangeSet> review(@PathVariable String id) {
		return ResponseEntity.ok(changeService.startReview(id));
	}

	@PostMapping("/changes/{id}/approve")
	public ResponseEntity<ChangeSet> approve(@PathVariable String id,
			@RequestBody(required = false) ChangeReviewRequest request) {
		return ResponseEntity.ok(changeService.approve(id, reviewer(request)));
	}

	@PostMapping("/changes/{id}/reject")
	public ResponseEntity<ChangeSet> reject(@PathVariable String id,
			@RequestBody(required = false) ChangeReviewRequest request) {
		return ResponseEntity.ok(changeService.reject(id, reviewer(request)));
	}

	private String reviewer(ChangeReviewRequest request) {
		return request == null ? null : request.reviewer();
	}
}
