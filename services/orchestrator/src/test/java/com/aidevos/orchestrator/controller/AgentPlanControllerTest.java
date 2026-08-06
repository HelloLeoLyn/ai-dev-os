package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.agentcoordinator.AgentCoordinatorService;
import com.aidevos.orchestrator.agentcoordinator.AgentExecutionPlan;
import com.aidevos.orchestrator.agentcoordinator.AgentPlanStatus;
import com.aidevos.orchestrator.modelrouter.TaskType;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AgentPlanControllerTest {

	@Test
	void shouldCreateAndRunCollaborationPlan() throws Exception {
		AgentCoordinatorService coordinator = mock(AgentCoordinatorService.class);
		when(coordinator.createCollaborationPlan(eq("task-1"), any(TaskType.class)))
			.thenReturn(steps());
		MockMvc mockMvc = standaloneSetup(new AgentPlanController(coordinator)).build();

		mockMvc.perform(post("/api/agent-plans/task-1")
				.param("taskType", "CODE_GENERATION"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$[0].agentId").value("codex"))
			.andExpect(jsonPath("$[0].step").value(1))
			.andExpect(jsonPath("$[0].status").value("SUCCESS"))
			.andExpect(jsonPath("$[1].agentId").value("openclaw"))
			.andExpect(jsonPath("$[1].step").value(2));
	}

	@Test
	void shouldGetCollaborationPlan() throws Exception {
		AgentCoordinatorService coordinator = mock(AgentCoordinatorService.class);
		when(coordinator.getCollaborationPlan("task-1"))
			.thenReturn(java.util.Optional.of(steps()));
		MockMvc mockMvc = standaloneSetup(new AgentPlanController(coordinator)).build();

		mockMvc.perform(get("/api/agent-plans/task-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].taskId").value("task-1"))
			.andExpect(jsonPath("$[0].agentId").value("codex"));
	}

	@Test
	void shouldReturn404WhenPlanMissing() throws Exception {
		AgentCoordinatorService coordinator = mock(AgentCoordinatorService.class);
		when(coordinator.getCollaborationPlan("missing"))
			.thenReturn(java.util.Optional.empty());
		MockMvc mockMvc = standaloneSetup(new AgentPlanController(coordinator)).build();

		mockMvc.perform(get("/api/agent-plans/missing"))
			.andExpect(status().isNotFound());
	}

	@Test
	void shouldReturn400WhenTaskMissing() throws Exception {
		AgentCoordinatorService coordinator = mock(AgentCoordinatorService.class);
		when(coordinator.createCollaborationPlan(eq("missing"), any(TaskType.class)))
			.thenThrow(new IllegalArgumentException("Task not found: missing"));
		MockMvc mockMvc = standaloneSetup(new AgentPlanController(coordinator)).build();

		mockMvc.perform(post("/api/agent-plans/missing"))
			.andExpect(status().isBadRequest());
	}

	private List<AgentExecutionPlan> steps() {
		AgentExecutionPlan codex = new AgentExecutionPlan("plan-1", "task-1", "codex", 1);
		codex.markRunning();
		codex.markSuccess("done");
		AgentExecutionPlan openclaw = new AgentExecutionPlan("plan-1", "task-1", "openclaw", 2);
		openclaw.markRunning();
		openclaw.markSuccess("navigated");
		return List.of(codex, openclaw);
	}
}
