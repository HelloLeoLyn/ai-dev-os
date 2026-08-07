package com.aidevos.orchestrator.controller;

import java.net.URI;
import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.schedule.ScheduleService;
import com.aidevos.orchestrator.schedule.ScheduledTask;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

	private final ScheduleService scheduleService;

	public ScheduleController(ScheduleService scheduleService) {
		this.scheduleService = scheduleService;
	}

	@PostMapping
	public ResponseEntity<ScheduledTask> create(@RequestBody ScheduledTask scheduledTask) {
		try {
			ScheduledTask created = scheduleService.register(scheduledTask);
			return ResponseEntity.created(URI.create("/api/schedules/" + created.getId()))
				.body(created);
		}
		catch (IllegalArgumentException ex) {
			return ResponseEntity.badRequest().build();
		}
	}

	@GetMapping
	public List<ScheduledTask> getAll() {
		return scheduleService.getAll();
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		if (!scheduleService.remove(id)) {
			throw new ResourceNotFoundException("Schedule", id);
		}
		return ResponseEntity.noContent().build();
	}
}
