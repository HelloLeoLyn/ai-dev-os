package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.collaboration.AgentCollaborationService;
import com.aidevos.orchestrator.collaboration.AgentMessage;
import com.aidevos.orchestrator.collaboration.AgentMessageType;
import com.aidevos.orchestrator.collaboration.AgentTeam;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Multi-agent collaboration API: reads the agent team and message trail of a
 * task and sends team messages / handoffs. Teams are created automatically by
 * the execution graph executor; errors are handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/collaboration")
public class CollaborationController {

	private final AgentCollaborationService collaborationService;

	public CollaborationController(AgentCollaborationService collaborationService) {
		this.collaborationService = collaborationService;
	}

	@GetMapping("/teams/{id}")
	public ResponseEntity<AgentTeam> getTeam(@PathVariable String id) {
		return ResponseEntity.ok(collaborationService.getTeam(id)
			.orElseThrow(() -> new ResourceNotFoundException("Agent team", id)));
	}

	@GetMapping("/tasks/{taskId}/team")
	public ResponseEntity<AgentTeam> teamForTask(@PathVariable String taskId) {
		return ResponseEntity.ok(collaborationService.teamForTask(taskId)
			.orElseThrow(() -> new ResourceNotFoundException("Agent team for task", taskId)));
	}

	@GetMapping("/teams/{id}/messages")
	public ResponseEntity<List<AgentMessage>> messages(@PathVariable String id) {
		if (collaborationService.getTeam(id).isEmpty()) {
			throw new ResourceNotFoundException("Agent team", id);
		}
		return ResponseEntity.ok(collaborationService.messages(id));
	}

	@PostMapping("/teams/{id}/message")
	public ResponseEntity<AgentMessage> sendMessage(@PathVariable String id,
			@RequestBody MessageRequest request) {
		AgentMessage message = collaborationService.sendMessage(id, request.fromAgent(),
			request.toAgent(), request.messageType(), request.content());
		return ResponseEntity.ok(message);
	}

	@PostMapping("/teams/{id}/handoff")
	public ResponseEntity<AgentMessage> handoff(@PathVariable String id,
			@RequestBody HandoffRequest request) {
		AgentMessage message = collaborationService.handoff(id, request.from(),
			request.to(), null);
		return ResponseEntity.ok(message);
	}

	public record MessageRequest(String fromAgent, String toAgent,
			AgentMessageType messageType, String content) {
	}

	public record HandoffRequest(String from, String to) {
	}
}
