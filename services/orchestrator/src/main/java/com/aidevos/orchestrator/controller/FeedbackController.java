package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.feedback.PrFeedbackRecord;
import com.aidevos.orchestrator.feedback.PrFeedbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pull request feedback loop API: inspect a feedback record, list the records
 * of a task and retry a FAILED feedback loop. Errors are handled by
 * GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api")
public class FeedbackController {

	private final PrFeedbackService feedbackService;

	public FeedbackController(PrFeedbackService feedbackService) {
		this.feedbackService = feedbackService;
	}

	@GetMapping("/feedback/{id}")
	public ResponseEntity<PrFeedbackRecord> get(@PathVariable String id) {
		return ResponseEntity.ok(feedbackService.get(id)
			.orElseThrow(() -> new ResourceNotFoundException("Feedback", id)));
	}

	@GetMapping("/tasks/{taskId}/feedback")
	public List<PrFeedbackRecord> listByTask(@PathVariable String taskId) {
		return feedbackService.getByTask(taskId);
	}

	@PostMapping("/feedback/{id}/retry")
	public ResponseEntity<PrFeedbackRecord> retry(@PathVariable String id) {
		return ResponseEntity.ok(feedbackService.retry(id));
	}
}
