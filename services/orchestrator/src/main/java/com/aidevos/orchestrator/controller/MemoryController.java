package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.memory.CreateMemoryRequest;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.MemoryType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {

	private final MemoryService memoryService;

	public MemoryController(MemoryService memoryService) {
		this.memoryService = memoryService;
	}

	@PostMapping
	public ResponseEntity<MemoryRecord> create(@RequestBody CreateMemoryRequest request) {
		MemoryRecord record = new MemoryRecord();
		record.setProjectId(request.projectId());
		record.setType(request.type());
		record.setKey(request.key());
		record.setContent(request.content());
		return ResponseEntity.status(HttpStatus.CREATED).body(memoryService.create(record));
	}

	@GetMapping
	public List<MemoryRecord> list(
			@RequestParam(required = false) String projectId,
			@RequestParam(required = false) MemoryType type) {
		return memoryService.list(projectId, type);
	}

	@GetMapping("/{id}")
	public ResponseEntity<MemoryRecord> get(@PathVariable String id) {
		MemoryRecord record = memoryService.get(id);
		return record == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(record);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		return memoryService.delete(id)
			? ResponseEntity.noContent().build()
			: ResponseEntity.notFound().build();
	}
}
