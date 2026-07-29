package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.task.TaskManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ExecutionControllerTest {

	@Test
	void shouldExecuteRegisteredTask() throws Exception {
		TaskManager taskManager = new TaskManager();
		TaskDefinition taskDefinition = new TaskDefinition();
		taskDefinition.setId("task-1");
		taskDefinition.setAgentName("planner");
		taskDefinition.setDescription("Create an implementation plan");
		taskManager.register(taskDefinition);

		AgentManager agentManager = new AgentManager();
		AgentDefinition agentDefinition = new AgentDefinition();
		agentDefinition.setName("planner");
		agentManager.register(agentDefinition);

		ExecutionEngine executionEngine = new ExecutionEngine(agentManager, new ExecutionRecordManager());
		MockMvc mockMvc = standaloneSetup(new ExecutionController(taskManager, executionEngine)).build();

		mockMvc.perform(post("/api/tasks/task-1/execute"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("Task executed successfully"));
	}

	@Test
	void shouldReturnNotFoundForUnknownTask() throws Exception {
		TaskManager taskManager = new TaskManager();
		ExecutionEngine executionEngine = new ExecutionEngine(new AgentManager(), new ExecutionRecordManager());
		MockMvc mockMvc = standaloneSetup(new ExecutionController(taskManager, executionEngine)).build();

		mockMvc.perform(post("/api/tasks/unknown/execute"))
			.andExpect(status().isNotFound());
	}
}
