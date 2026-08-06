package com.aidevos.orchestrator.controller;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.dashboard.AgentRegistryService;
import com.aidevos.orchestrator.dashboard.AgentRuntimeStatus;
import com.aidevos.orchestrator.dashboard.AgentStatusDTO;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentControllerTest {

	@Test
	void shouldReturnAllAgents() throws Exception {
		AgentManager agentManager = new AgentManager();
		AgentDefinition agentDefinition = new AgentDefinition();
		agentDefinition.setName("planner");
		agentManager.register(agentDefinition);
		MockMvc mockMvc = standaloneSetup(
			new AgentController(agentManager, mock(AgentRegistryService.class))).build();

		mockMvc.perform(get("/api/agents"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("planner"));
	}

	@Test
	void shouldReturnAgentRegistry() throws Exception {
		AgentRegistryService registryService = mock(AgentRegistryService.class);
		when(registryService.listAgents()).thenReturn(List.of(new AgentStatusDTO(
			"main", "tester", "system", AgentRuntimeStatus.RUNNING, true,
			List.of("testing", "browser"), Instant.parse("2026-08-01T00:00:00Z"))));
		MockMvc mockMvc = standaloneSetup(
			new AgentController(new AgentManager(), registryService)).build();

		mockMvc.perform(get("/api/dashboard/agents"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].agentId").value("main"))
			.andExpect(jsonPath("$[0].name").value("tester"))
			.andExpect(jsonPath("$[0].type").value("system"))
			.andExpect(jsonPath("$[0].status").value("RUNNING"))
			.andExpect(jsonPath("$[0].enabled").value(true))
			.andExpect(jsonPath("$[0].capabilities[0]").value("testing"))
			.andExpect(jsonPath("$[0].lastHeartbeat").value("2026-08-01T00:00:00Z"));

		verify(registryService).listAgents();
	}
}
