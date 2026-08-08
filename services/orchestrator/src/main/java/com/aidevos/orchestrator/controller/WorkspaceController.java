package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.workspace.CreateWorkspaceRequest;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.git.GitDiff;
import com.aidevos.orchestrator.workspace.git.GitStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Workspace management API: register existing local directories as workspaces
 * and inspect their git state. Errors are handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

	private final WorkspaceService workspaceService;

	public WorkspaceController(WorkspaceService workspaceService) {
		this.workspaceService = workspaceService;
	}

	@PostMapping
	public ResponseEntity<Workspace> create(@RequestBody CreateWorkspaceRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(workspaceService.createWorkspace(request.projectId(), request.path()));
	}

	@GetMapping
	public List<Workspace> list() {
		return workspaceService.listWorkspaces();
	}

	@GetMapping("/{id}")
	public ResponseEntity<Workspace> get(@PathVariable String id) {
		return ResponseEntity.ok(workspaceService.getWorkspace(id)
			.orElseThrow(() -> new ResourceNotFoundException("Workspace", id)));
	}

	@GetMapping("/{id}/git/status")
	public ResponseEntity<GitStatus> gitStatus(@PathVariable String id) {
		return ResponseEntity.ok(workspaceService.checkGitStatus(id));
	}

	@GetMapping("/{id}/git/diff")
	public ResponseEntity<GitDiff> gitDiff(@PathVariable String id) {
		return ResponseEntity.ok(workspaceService.getGitDiff(id));
	}
}
