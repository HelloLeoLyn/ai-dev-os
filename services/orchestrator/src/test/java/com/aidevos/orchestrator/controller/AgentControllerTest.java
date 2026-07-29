package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AgentControllerTest {

	@Test
	void shouldReturnAllAgents() throws Exception {
		AgentManager agentManager = new AgentManager();
		AgentDefinition agentDefinition = new AgentDefinition();
		agentDefinition.setName("planner");
		agentManager.register(agentDefinition);
		MockMvc mockMvc = standaloneSetup(new AgentController(agentManager)).build();

		mockMvc.perform(get("/api/agents"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("planner"));
	}
}
