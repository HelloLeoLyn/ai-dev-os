package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.project.CreateProjectRequest;
import com.aidevos.orchestrator.project.Project;
import com.aidevos.orchestrator.project.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project management API: create/list projects and switch the current project
 * or archive a project.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@PostMapping
	public ResponseEntity<Project> create(@RequestBody CreateProjectRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(projectService.createProject(request));
	}

	@GetMapping
	public List<Project> list() {
		return projectService.listProjects();
	}

	@GetMapping("/{id}")
	public ResponseEntity<Project> get(@PathVariable String id) {
		return projectService.getProject(id)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/{id}/active")
	public ResponseEntity<Project> setActive(@PathVariable String id) {
		return projectService.setActive(id)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/{id}/archive")
	public ResponseEntity<Project> archive(@PathVariable String id) {
		return projectService.archive(id)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Void> handleIllegalArgument(IllegalArgumentException exception) {
		return ResponseEntity.badRequest().build();
	}
}
