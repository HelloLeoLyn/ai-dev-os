package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.execution.query.ExecutionRecordDetail;
import com.aidevos.orchestrator.execution.query.ExecutionRecordQueryService;
import com.aidevos.orchestrator.execution.query.ExecutionRecordSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/execution-records")
public class ExecutionRecordController {

	private final ExecutionRecordQueryService queryService;

	public ExecutionRecordController(ExecutionRecordQueryService queryService) {
		this.queryService = queryService;
	}

	@GetMapping
	public List<ExecutionRecordSummary> getAll(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String taskId) {
		return queryService.getAll(status, taskId);
	}

	@GetMapping("/{id}")
	public ResponseEntity<ExecutionRecordDetail> get(@PathVariable String id) {
		return queryService.get(id)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
