package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.planner.PlannerService;
import com.aidevos.orchestrator.planner.PlanningResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanningControllerTest {

	@Test
	void createsPlanFromUserRequest() throws Exception {
		PlannerService service = mock(PlannerService.class);
		when(service.createPlan(any())).thenReturn(
			PlanningResult.failure("hermes", null, List.of("INVALID")));
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new PlanningController(service)).build();

		mvc.perform(post("/api/planning").contentType("application/json").content("""
			{"requestId":"request-1","goal":"Inspect and fix","plannerName":"hermes"}
			"""))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.plannerName").value("hermes"))
			.andExpect(jsonPath("$.errors[0]").value("INVALID"));
	}
}
