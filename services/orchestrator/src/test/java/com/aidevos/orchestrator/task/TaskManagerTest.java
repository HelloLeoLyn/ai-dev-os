package com.aidevos.orchestrator.task;

import com.aidevos.orchestrator.model.TaskDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TaskManagerTest {

	@Test
	void shouldRegisterAndGetTask() {
		TaskManager taskManager = new TaskManager();
		TaskDefinition taskDefinition = createTask("task-1");

		taskManager.register(taskDefinition);

		assertSame(taskDefinition, taskManager.getTask("task-1"));
	}

	@Test
	void shouldGetAllTasks() {
		TaskManager taskManager = new TaskManager();
		TaskDefinition firstTask = createTask("task-1");
		TaskDefinition secondTask = createTask("task-2");

		taskManager.register(firstTask);
		taskManager.register(secondTask);

		assertEquals(List.of(firstTask, secondTask), taskManager.getAllTasks());
	}

	@Test
	void shouldRemoveTask() {
		TaskManager taskManager = new TaskManager();
		TaskDefinition taskDefinition = createTask("task-1");
		taskManager.register(taskDefinition);

		TaskDefinition removedTask = taskManager.removeTask("task-1");

		assertSame(taskDefinition, removedTask);
		assertNull(taskManager.getTask("task-1"));
		assertEquals(List.of(), taskManager.getAllTasks());
	}

	@Test
	void shouldUpdateTaskStatus() {
		TaskManager taskManager = new TaskManager();
		TaskDefinition taskDefinition = createTask("task-1");
		taskDefinition.setStatus("pending");
		taskManager.register(taskDefinition);

		taskManager.updateStatus("task-1", "completed");

		assertEquals("completed", taskDefinition.getStatus());
	}

	@Test
	void shouldIgnoreStatusUpdateForUnknownTask() {
		TaskManager taskManager = new TaskManager();

		taskManager.updateStatus("unknown", "completed");

		assertEquals(List.of(), taskManager.getAllTasks());
	}

	private TaskDefinition createTask(String id) {
		TaskDefinition taskDefinition = new TaskDefinition();
		taskDefinition.setId(id);
		return taskDefinition;
	}
}
