package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.delivery.DeliveryPipeline;
import com.aidevos.orchestrator.delivery.DeliveryPipelineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Delivery pipeline API: explicit advance trigger plus read of the persisted
 * aggregate. advance() is idempotent; deterministic stages run automatically
 * and the pipeline stops at human gates or on failure.
 */
@RestController
@RequestMapping("/api")
public class DeliveryController {

	private final DeliveryPipelineService service;

	public DeliveryController(DeliveryPipelineService service) {
		this.service = service;
	}

	@PostMapping("/tasks/{taskId}/delivery/advance")
	public ResponseEntity<DeliveryPipeline> advance(@PathVariable String taskId) {
		return ResponseEntity.ok(service.advance(taskId));
	}

	@GetMapping("/tasks/{taskId}/delivery")
	public ResponseEntity<DeliveryPipeline> get(@PathVariable String taskId) {
		DeliveryPipeline pipeline = service.get(taskId);
		if (pipeline == null) {
			throw new ResourceNotFoundException("DeliveryPipeline", taskId);
		}
		return ResponseEntity.ok(pipeline);
	}
}
