package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.ci.CiRunRecord;
import com.aidevos.orchestrator.ci.CiService;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CI status API: check the CI run of a pull request and inspect CI run
 * records. Errors are handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api")
public class CiController {

	private final CiService ciService;

	public CiController(CiService ciService) {
		this.ciService = ciService;
	}

	@GetMapping("/ci/{id}")
	public ResponseEntity<CiRunRecord> get(@PathVariable String id) {
		return ResponseEntity.ok(ciService.get(id)
			.orElseThrow(() -> new ResourceNotFoundException("CiRun", id)));
	}

	@GetMapping("/tasks/{taskId}/ci")
	public List<CiRunRecord> listByTask(@PathVariable String taskId) {
		return ciService.getByTask(taskId);
	}

	@PostMapping("/pull-requests/{id}/ci/check")
	public ResponseEntity<CiRunRecord> check(@PathVariable String id) {
		return ResponseEntity.ok(ciService.check(id));
	}
}
