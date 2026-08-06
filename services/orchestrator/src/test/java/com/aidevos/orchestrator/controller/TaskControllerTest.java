package com.aidevos.orchestrator.controller;

import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskControllerTest {

	@Test
	void shouldCreateTask() throws Exception {
		TaskCenterService service = mock(TaskCenterService.class);
		TaskRecord task = new TaskRecord("task-1", "Implement login", "Login flow");
		task.markPlanning("approval-1");
		when(service.createTask(any())).thenReturn(task);
		MockMvc mockMvc = standaloneSetup(new TaskController(service)).build();
		String requestBody = """
				{
				  "name": "Implement login",
				  "description": "Login flow",
				  "goal": "Implement a login flow",
				  "plannerName": "hermes"
				}
				""";

		mockMvc.perform(post("/api/tasks")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.taskId").value("task-1"))
			.andExpect(jsonPath("$.name").value("Implement login"))
			.andExpect(jsonPath("$.status").value("PLANNING"))
			.andExpect(jsonPath("$.approvalId").value("approval-1"));

		verify(service).createTask(any());
	}

	@Test
	void shouldReturnTaskList() throws Exception {
		TaskCenterService service = mock(TaskCenterService.class);
		TaskRecord task = new TaskRecord("task-1", "Implement login", "Login flow");
		when(service.listTasks()).thenReturn(List.of(task));
		MockMvc mockMvc = standaloneSetup(new TaskController(service)).build();

		mockMvc.perform(get("/api/tasks"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].taskId").value("task-1"))
			.andExpect(jsonPath("$[0].status").value("CREATED"));

		verify(service).listTasks();
	}

	@Test
	void shouldReturnTaskDetail() throws Exception {
		TaskCenterService service = mock(TaskCenterService.class);
		TaskRecord task = new TaskRecord("task-1", "Implement login", "Login flow");
		task.markSuccess();
		when(service.getTask("task-1")).thenReturn(Optional.of(task));
		MockMvc mockMvc = standaloneSetup(new TaskController(service)).build();

		mockMvc.perform(get("/api/tasks/task-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.taskId").value("task-1"))
			.andExpect(jsonPath("$.status").value("SUCCESS"));

		verify(service).getTask("task-1");
	}

	@Test
	void shouldReturnNotFoundForUnknownTask() throws Exception {
		TaskCenterService service = mock(TaskCenterService.class);
		when(service.getTask("missing")).thenReturn(Optional.empty());
		MockMvc mockMvc = standaloneSetup(new TaskController(service)).build();

		mockMvc.perform(get("/api/tasks/missing"))
			.andExpect(status().isNotFound());
	}

	@Test
	void shouldDefaultTaskStatusToCreated() {
		TaskRecord task = new TaskRecord("task-1", "name", "description");

		org.junit.jupiter.api.Assertions.assertEquals(TaskStatus.CREATED, task.getStatus());
	}
}
