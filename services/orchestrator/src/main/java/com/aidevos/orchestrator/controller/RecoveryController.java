package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.recovery.RecoveryAttempt;
import com.aidevos.orchestrator.recovery.RecoveryCoordinator;
import com.aidevos.orchestrator.recovery.RecoveryDecision;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recovery V1：Failure Diagnosis 下的 Recovery 入口（决策 + 受限自动执行）。
 */
@RestController
@RequestMapping("/api/tasks")
public class RecoveryController {

	private final RecoveryCoordinator coordinator;

	public RecoveryController(RecoveryCoordinator coordinator) {
		this.coordinator = coordinator;
	}

	@GetMapping("/{taskId}/recovery")
	public RecoveryDecision decision(@PathVariable String taskId) {
		return coordinator.decide(taskId);
	}

	@PostMapping("/{taskId}/recovery/evaluate")
	public RecoveryAttempt evaluate(@PathVariable String taskId) {
		return coordinator.evaluate(taskId);
	}
}
