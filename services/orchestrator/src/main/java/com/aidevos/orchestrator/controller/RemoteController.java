package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.remote.RemoteBranchRecord;
import com.aidevos.orchestrator.remote.RemoteGitService;
import com.aidevos.orchestrator.remote.RemotePushRequest;
import com.aidevos.orchestrator.remote.RemotePushApproval;
import com.aidevos.orchestrator.remote.RemotePushApprovalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Remote git API: push a committed change to a remote branch and inspect push
 * records. Errors are handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api")
public class RemoteController {

	private final RemoteGitService remoteGitService;
	private final RemotePushApprovalService pushApprovalService;

	public RemoteController(RemoteGitService remoteGitService) { this(remoteGitService, null); }
	@Autowired
	public RemoteController(RemoteGitService remoteGitService, RemotePushApprovalService pushApprovalService) {
		this.remoteGitService = remoteGitService;
		this.pushApprovalService = pushApprovalService;
	}

	@PostMapping("/commits/{id}/push")
	public ResponseEntity<RemoteBranchRecord> push(@PathVariable String id,
			@RequestBody(required = false) RemotePushRequest request) {
		String remote=request == null ? null : request.remote();
	String approval=request == null ? null : request.approvalId();
	return ResponseEntity.ok(approval == null ? remoteGitService.push(id, remote) : remoteGitService.push(id, remote, approval));
	}
	@PostMapping("/commits/{id}/push/approval")
	public ResponseEntity<RemotePushApproval> requestApproval(@PathVariable String id,
			@RequestBody(required=false) RemotePushRequest request) {
		return ResponseEntity.ok(remoteGitService.requestApproval(id, request == null ? null : request.remote()));
	}
	@PostMapping("/remote-push-approvals/{id}/approve")
	public ResponseEntity<RemotePushApproval> approve(@PathVariable String id){return ResponseEntity.ok(pushApprovalService.approve(id));}
	@PostMapping("/remote-push-approvals/{id}/reject")
	public ResponseEntity<RemotePushApproval> reject(@PathVariable String id){return ResponseEntity.ok(pushApprovalService.reject(id));}

	@GetMapping("/remotes/{id}")
	public ResponseEntity<RemoteBranchRecord> get(@PathVariable String id) {
		return ResponseEntity.ok(remoteGitService.get(id)
			.orElseThrow(() -> new ResourceNotFoundException("Remote", id)));
	}

	@GetMapping("/tasks/{taskId}/remotes")
	public List<RemoteBranchRecord> listByTask(@PathVariable String taskId) {
		return remoteGitService.getByTask(taskId);
	}
	@GetMapping("/tasks/{taskId}/remote-push-approvals")
	public List<RemotePushApproval> approvals(@PathVariable String taskId){return pushApprovalService.getByTask(taskId);}
}
