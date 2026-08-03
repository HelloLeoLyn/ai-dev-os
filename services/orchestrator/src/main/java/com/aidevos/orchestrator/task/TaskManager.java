package com.aidevos.orchestrator.task;

import com.aidevos.orchestrator.model.TaskDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class TaskManager {

	private final TaskRepository repository;

	public TaskManager() { this(new InMemoryTaskRepository()); }

	@Autowired
	public TaskManager(TaskRepository repository) { this.repository = repository; }

	public synchronized void register(TaskDefinition taskDefinition) {
		repository.save(taskDefinition);
	}

	public synchronized TaskDefinition getTask(String id) {
		return repository.get(id);
	}

	public synchronized List<TaskDefinition> getAllTasks() {
		return repository.getAll();
	}

	public synchronized TaskDefinition removeTask(String id) {
		TaskDefinition existing = repository.get(id);
		repository.remove(id);
		return existing;
	}

	public synchronized void updateStatus(String id, String status) {
		TaskDefinition taskDefinition = repository.get(id);
		if (taskDefinition != null) {
			taskDefinition.setStatus(status);
			repository.save(taskDefinition);
		}
	}
}
