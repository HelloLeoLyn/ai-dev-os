package com.aidevos.orchestrator.project;

import java.util.List;

import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.springframework.stereotype.Service;

/**
 * Project-scoped task queries: lists the tasks belonging to one project
 * without touching the global task flow.
 */
@Service
public class ProjectTaskService {

	private final TaskCenterService taskCenterService;

	public ProjectTaskService(TaskCenterService taskCenterService) {
		this.taskCenterService = taskCenterService;
	}

	public List<TaskRecord> listTasksByProject(String projectId) {
		if (projectId == null || projectId.isBlank()) {
			return List.of();
		}
		return taskCenterService.listTasks().stream()
			.filter(task -> projectId.equals(task.getProjectId()))
			.toList();
	}
}
