package com.aidevos.orchestrator.task;

import com.aidevos.orchestrator.model.TaskDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskLoaderTest {

	@Test
	void shouldLoadTaskFromResourcesDirectory() {
		TaskManager taskManager = new TaskManager();
		TaskLoader taskLoader = new TaskLoader(
				new org.springframework.core.io.support.PathMatchingResourcePatternResolver(),
				new ObjectMapper(), taskManager);

		taskLoader.loadTasks();

		TaskDefinition task = taskManager.getTask("openclaw-test");
		assertNotNull(task);
		assertEquals("OpenClaw Browser Test", task.getName());
		assertEquals("browser-agent", task.getAgentName());
		assertEquals("navigate", ((java.util.Map<?, ?>) task.getParameters().get("browser")).get("action"));
		assertEquals("https://example.com",
			((java.util.Map<?, ?>) task.getParameters().get("browser")).get("url"));
	}

	@Test
	void shouldContinueLoadingAfterInvalidTask() throws IOException {
		Resource invalidTask = resource("invalid.json", "{invalid-json}");
		Resource validTask = resource("valid.json", """
				{
				  "id": "valid-task",
				  "name": "Valid task",
				  "agentName": "tester",
				  "description": "Still loaded"
				}
				""");
		ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
		when(resolver.getResources(anyString())).thenReturn(new Resource[] { invalidTask, validTask });
		TaskManager taskManager = new TaskManager();

		new TaskLoader(resolver, new ObjectMapper(), taskManager).loadTasks();

		assertNull(taskManager.getTask("invalid-task"));
		assertNotNull(taskManager.getTask("valid-task"));
		assertEquals(1, taskManager.getAllTasks().size());
	}

	private Resource resource(String filename, String content) {
		return new ByteArrayResource(content.getBytes()) {
			@Override
			public String getFilename() {
				return filename;
			}
		};
	}
}
