package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.runtime.AgentSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent runtime API: starts a long-running runtime session for a task and
 * drives the session lifecycle (pause / resume / stop). Session state and
 * node checkpoints stay in the in-memory runtime repository; errors are
 * handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/runtime")
public class RuntimeController {

	private final AgentRuntimeService runtimeService;

	public RuntimeController(AgentRuntimeService runtimeService) {
		this.runtimeService = runtimeService;
	}

	@GetMapping("/sessions/{id}")
	public ResponseEntity<AgentSession> getSession(@PathVariable String id) {
		return ResponseEntity.ok(runtimeService.getSession(id)
			.orElseThrow(() -> new ResourceNotFoundException("Runtime session", id)));
	}

	@PostMapping("/tasks/{taskId}/start")
	public ResponseEntity<AgentSession> start(@PathVariable String taskId) {
		return ResponseEntity.ok(runtimeService.startSession(taskId));
	}

	@PostMapping("/sessions/{id}/pause")
	public ResponseEntity<AgentSession> pause(@PathVariable String id) {
		return ResponseEntity.ok(runtimeService.pauseSession(id));
	}

	@PostMapping("/sessions/{id}/resume")
	public ResponseEntity<AgentSession> resume(@PathVariable String id) {
		return ResponseEntity.ok(runtimeService.resumeSession(id));
	}

	@PostMapping("/sessions/{id}/stop")
	public ResponseEntity<AgentSession> stop(@PathVariable String id) {
		return ResponseEntity.ok(runtimeService.stopSession(id));
	}
}
