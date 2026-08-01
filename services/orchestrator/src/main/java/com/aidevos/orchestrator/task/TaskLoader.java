package com.aidevos.orchestrator.task;

import com.aidevos.orchestrator.model.TaskDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
public class TaskLoader implements ApplicationRunner {

	static final String TASK_RESOURCE_PATTERN = "classpath:/tasks/*.json";

	private static final Logger logger = LoggerFactory.getLogger(TaskLoader.class);

	private final ResourcePatternResolver resourcePatternResolver;
	private final ObjectMapper objectMapper;
	private final TaskManager taskManager;

	public TaskLoader(ResourcePatternResolver resourcePatternResolver, ObjectMapper objectMapper,
			TaskManager taskManager) {
		this.resourcePatternResolver = resourcePatternResolver;
		this.objectMapper = objectMapper;
		this.taskManager = taskManager;
	}

	@Override
	public void run(ApplicationArguments args) {
		loadTasks();
	}

	public void loadTasks() {
		Resource[] resources;
		try {
			resources = resourcePatternResolver.getResources(TASK_RESOURCE_PATTERN);
		}
		catch (IOException exception) {
			logger.error("Failed to scan task resources from {}", TASK_RESOURCE_PATTERN, exception);
			return;
		}

		for (Resource resource : resources) {
			loadTask(resource);
		}
	}

	private void loadTask(Resource resource) {
		try (var inputStream = resource.getInputStream()) {
			TaskDefinition taskDefinition = objectMapper.readValue(inputStream, TaskDefinition.class);
			taskManager.register(taskDefinition);
			logger.info("Loaded task '{}' from {}", taskDefinition.getId(), resource.getDescription());
		}
		catch (Exception exception) {
			logger.error("Failed to load task from {}", resource.getDescription(), exception);
		}
	}
}
