package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.memory.CreateMemoryRequest;
import com.aidevos.orchestrator.memory.MemoryContext;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.MemoryType;
import com.aidevos.orchestrator.memory.search.MemoryMatch;
import com.aidevos.orchestrator.memory.search.MemoryQuery;
import com.aidevos.orchestrator.memory.search.MemorySearchService;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.springframework.beans.factory.annotation.Autowired;
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
	private final MemorySearchService searchService;
	private final TaskCenterService taskCenterService;

	public MemoryController(MemoryService memoryService) {
		this(memoryService, null, null);
	}

	@Autowired
	public MemoryController(MemoryService memoryService, MemorySearchService searchService,
			TaskCenterService taskCenterService) {
		this.memoryService = memoryService;
		this.searchService = searchService;
		this.taskCenterService = taskCenterService;
	}

	@GetMapping("/search")
	public List<MemoryMatch> search(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String agentType,
			@RequestParam(required = false, defaultValue = "10") int limit,
			@RequestParam(required = false) String projectId) {
		if (searchService == null) {
			return List.of();
		}
		return searchService.search(new MemoryQuery(q, type, agentType, projectId, limit));
	}

	@GetMapping("/tasks/{taskId}/memory")
	public MemoryContext taskMemory(@PathVariable String taskId) {
		if (taskCenterService == null || searchService == null) {
			return new MemoryContext();
		}
		TaskRecord task = taskCenterService.getTask(taskId)
			.orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
		String query = task.getDescription() == null || task.getDescription().isBlank()
			? task.getName() : task.getDescription();
		return searchService.taskContext(task.getTaskId(), task.getProjectId(), query);
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
		if (record == null) {
			throw new ResourceNotFoundException("Memory record", id);
		}
		return ResponseEntity.ok(record);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		if (!memoryService.delete(id)) {
			throw new ResourceNotFoundException("Memory record", id);
		}
		return ResponseEntity.noContent().build();
	}
}
