package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.commit.CommitRecord;
import com.aidevos.orchestrator.commit.CommitService;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Commit flow API: commit an approved change set and inspect commit records.
 * Errors are handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api")
public class CommitController {

	private final CommitService commitService;

	public CommitController(CommitService commitService) {
		this.commitService = commitService;
	}

	@PostMapping("/changes/{id}/commit")
	public ResponseEntity<CommitRecord> commit(@PathVariable String id) {
		return ResponseEntity.ok(commitService.commit(id));
	}

	@GetMapping("/commits/{id}")
	public ResponseEntity<CommitRecord> get(@PathVariable String id) {
		return ResponseEntity.ok(commitService.getCommit(id)
			.orElseThrow(() -> new ResourceNotFoundException("Commit", id)));
	}

	@GetMapping("/tasks/{taskId}/commits")
	public List<CommitRecord> listByTask(@PathVariable String taskId) {
		return commitService.getCommitsByTask(taskId);
	}
}
