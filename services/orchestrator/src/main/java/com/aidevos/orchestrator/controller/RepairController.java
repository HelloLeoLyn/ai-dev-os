package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.repair.RepairCoordinator;
import com.aidevos.orchestrator.repair.RepairTask;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Automatic repair API: start a repair loop for a failed task and inspect the
 * current repair state (status, retry count, failure context, last result).
 * Errors are handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/repair")
public class RepairController {

	private final RepairCoordinator repairCoordinator;

	public RepairController(RepairCoordinator repairCoordinator) {
		this.repairCoordinator = repairCoordinator;
	}

	@PostMapping("/{taskId}")
	public ResponseEntity<RepairTask> start(@PathVariable String taskId) {
		return ResponseEntity.ok(repairCoordinator.start(taskId));
	}

	@GetMapping("/{taskId}")
	public ResponseEntity<RepairTask> get(@PathVariable String taskId) {
		return ResponseEntity.ok(repairCoordinator.get(taskId)
			.orElseThrow(() -> new ResourceNotFoundException("Repair", taskId)));
	}
}
