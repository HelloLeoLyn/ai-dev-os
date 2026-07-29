package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.task.TaskManager;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class TaskControllerTest {

	@Test
	void shouldRegisterAndReturnTasks() throws Exception {
		TaskManager taskManager = new TaskManager();
		MockMvc mockMvc = standaloneSetup(new TaskController(taskManager)).build();
		String requestBody = """
				{
				  "id": "task-1",
				  "name": "Plan implementation",
				  "description": "Create an implementation plan",
				  "agentName": "planner",
				  "status": "pending"
				}
				""";

		mockMvc.perform(post("/api/tasks")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("task-1"));

		assertNotNull(taskManager.getTask("task-1"));

		mockMvc.perform(get("/api/tasks"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value("task-1"))
			.andExpect(jsonPath("$[0].agentName").value("planner"));
	}
}
