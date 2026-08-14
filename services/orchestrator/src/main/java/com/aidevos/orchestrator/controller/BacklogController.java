package com.aidevos.orchestrator.controller;

import java.util.List;
import com.aidevos.orchestrator.backlog.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/backlog")
public class BacklogController {
	private final BacklogService service;
	public BacklogController(BacklogService service) { this.service = service; }

	@PostMapping
	public ResponseEntity<BacklogItem> create(@RequestBody CreateBacklogRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
	}

	@GetMapping
	public List<BacklogItem> list(@RequestParam(required = false) BacklogStatus status,
			@RequestParam(required = false) BacklogPriority priority,
			@RequestParam(required = false) String projectId,
			@RequestParam(required = false) BacklogSourceType sourceType) {
		return service.list(status, priority, projectId, sourceType);
	}

	@GetMapping("/{id}") public BacklogItem get(@PathVariable String id) { return service.get(id); }
	@PutMapping("/{id}") public BacklogItem update(@PathVariable String id,
			@RequestBody UpdateBacklogRequest request) { return service.update(id, request); }
	@PostMapping("/{id}/status") public BacklogItem status(@PathVariable String id,
			@RequestBody ChangeBacklogStatusRequest request) { return service.changeStatus(id, request); }
	@PostMapping("/{id}/convert-to-task") public BacklogConversionResult convert(@PathVariable String id,
			@RequestBody ConvertBacklogToTaskRequest request) { return service.convertToTask(id, request); }
}
