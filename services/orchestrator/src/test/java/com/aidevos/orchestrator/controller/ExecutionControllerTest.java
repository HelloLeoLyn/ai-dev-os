package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;

import com.aidevos.orchestrator.agent.AgentResolver;
import com.aidevos.orchestrator.agent.AgentSelector;
import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.executor.ExecutorRegistry;
import com.aidevos.orchestrator.executor.MockAgentExecutor;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.modelrouter.TaskType;
import com.aidevos.orchestrator.task.TaskManager;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
		agentDefinition.setExecutor("mock");
		agentManager.register(agentDefinition);

		ExecutionEngine executionEngine = createExecutionEngine(agentManager);
		MockMvc mockMvc = standaloneSetup(new ExecutionController(taskManager, executionEngine)).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(post("/api/tasks/task-1/execute"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("Task executed successfully"));
	}

	@Test
	void shouldReturnNotFoundForUnknownTask() throws Exception {
		TaskManager taskManager = new TaskManager();
		AgentManager agentManager = new AgentManager();
		ExecutionEngine executionEngine = createExecutionEngine(agentManager);
		MockMvc mockMvc = standaloneSetup(new ExecutionController(taskManager, executionEngine)).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(post("/api/tasks/unknown/execute"))
			.andExpect(status().isNotFound());
	}

	@Test
	void shouldDispatchTaskCenterTaskToClosedLoop() throws Exception {
		TaskManager taskManager = new TaskManager();
		AgentManager agentManager = new AgentManager();
		ExecutionEngine executionEngine = createExecutionEngine(agentManager);
		TaskCenterService taskCenterService = mock(TaskCenterService.class);
		TaskRecord task = new TaskRecord("task-tc-1", "Implement login", "Login flow");
		task.markApproved();
		when(taskCenterService.getTask("task-tc-1")).thenReturn(Optional.of(task));
		when(taskCenterService.execute("task-tc-1", TaskType.GENERAL)).thenReturn(task);
		MockMvc mockMvc = standaloneSetup(
			new ExecutionController(taskManager, executionEngine, taskCenterService))
			.setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(post("/api/tasks/task-tc-1/execute"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.taskId").value("task-tc-1"))
			.andExpect(jsonPath("$.status").value("APPROVED"));

		verify(taskCenterService).execute("task-tc-1", TaskType.GENERAL);
	}

	@Test
	void shouldFallBackToLegacyExecutionForNonTaskCenterTask() throws Exception {
		TaskManager taskManager = new TaskManager();
		TaskDefinition taskDefinition = new TaskDefinition();
		taskDefinition.setId("task-1");
		taskDefinition.setAgentName("planner");
		taskDefinition.setDescription("Create an implementation plan");
		taskManager.register(taskDefinition);

		AgentManager agentManager = new AgentManager();
		AgentDefinition agentDefinition = new AgentDefinition();
		agentDefinition.setName("planner");
		agentDefinition.setExecutor("mock");
		agentManager.register(agentDefinition);

		ExecutionEngine executionEngine = createExecutionEngine(agentManager);
		TaskCenterService taskCenterService = mock(TaskCenterService.class);
		when(taskCenterService.getTask("task-1")).thenReturn(Optional.empty());
		MockMvc mockMvc = standaloneSetup(
			new ExecutionController(taskManager, executionEngine, taskCenterService))
			.setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(post("/api/tasks/task-1/execute"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("Task executed successfully"));
	}

	private ExecutionEngine createExecutionEngine(AgentManager agentManager) {
		ExecutorManager executorManager = new ExecutorManager(agentManager,
			new ExecutorRegistry(List.of(new MockAgentExecutor())));
		AgentResolver agentResolver = new AgentResolver(agentManager,
			new AgentSelector(agentManager), executorManager);
		return new ExecutionEngine(agentResolver, new ExecutionRecordManager());
	}
}
