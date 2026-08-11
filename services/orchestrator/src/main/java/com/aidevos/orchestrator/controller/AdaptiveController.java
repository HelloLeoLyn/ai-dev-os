package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.adaptive.AdaptationDecision;
import com.aidevos.orchestrator.adaptive.AdaptiveExecutionService;
import com.aidevos.orchestrator.adaptive.ExecutionFeedback;
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
 * Adaptive execution API: reads the feedback / decisions / replans of a task
 * and triggers an execution analysis that returns the recommended
 * adaptation. Errors are handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/adaptive")
public class AdaptiveController {

	private final AdaptiveExecutionService adaptiveService;
	private final AgentRuntimeService runtimeService;

	public AdaptiveController(AdaptiveExecutionService adaptiveService,
			AgentRuntimeService runtimeService) {
		this.adaptiveService = adaptiveService;
		this.runtimeService = runtimeService;
	}

	@GetMapping("/tasks/{taskId}")
	public ResponseEntity<TaskAdaptiveView> task(@PathVariable String taskId) {
		return ResponseEntity.ok(new TaskAdaptiveView(taskId,
			adaptiveService.feedbacksForTask(taskId),
			adaptiveService.decisionsForTask(taskId),
			adaptiveService.replansForTask(taskId)));
	}

	/**
	 * Analyzes the latest runtime session of the task and returns the
	 * recommended adaptation decision (or a 404 when the task has no session
	 * or no feedback yet).
	 */
	@PostMapping("/tasks/{taskId}/analyze")
	public ResponseEntity<AdaptationDecision> analyze(@PathVariable String taskId) {
		AgentSession session = runtimeService.sessionsForTask(taskId).stream()
			.reduce((first, second) -> second)
			.orElseThrow(() -> new ResourceNotFoundException("Runtime session", taskId));
		AdaptationDecision decision = adaptiveService.analyzeExecution(session.getSessionId());
		if (decision == null) {
			throw new ResourceNotFoundException("Execution feedback", taskId);
		}
		return ResponseEntity.ok(decision);
	}

	public record TaskAdaptiveView(String taskId, List<ExecutionFeedback> feedback,
			List<AdaptationDecision> adaptations, List<String> replans) {
	}
}
