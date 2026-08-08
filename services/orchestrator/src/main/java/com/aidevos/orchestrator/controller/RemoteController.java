package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.remote.RemoteBranchRecord;
import com.aidevos.orchestrator.remote.RemoteGitService;
import com.aidevos.orchestrator.remote.RemotePushRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Remote git API: push a committed change to a remote branch and inspect push
 * records. Errors are handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api")
public class RemoteController {

	private final RemoteGitService remoteGitService;

	public RemoteController(RemoteGitService remoteGitService) {
		this.remoteGitService = remoteGitService;
	}

	@PostMapping("/commits/{id}/push")
	public ResponseEntity<RemoteBranchRecord> push(@PathVariable String id,
			@RequestBody(required = false) RemotePushRequest request) {
		return ResponseEntity.ok(remoteGitService.push(id, request == null ? null
			: request.remote()));
	}

	@GetMapping("/remotes/{id}")
	public ResponseEntity<RemoteBranchRecord> get(@PathVariable String id) {
		return ResponseEntity.ok(remoteGitService.get(id)
			.orElseThrow(() -> new ResourceNotFoundException("Remote", id)));
	}

	@GetMapping("/tasks/{taskId}/remotes")
	public List<RemoteBranchRecord> listByTask(@PathVariable String taskId) {
		return remoteGitService.getByTask(taskId);
	}
}
