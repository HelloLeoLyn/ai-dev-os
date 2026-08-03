package com.aidevos.orchestrator.controller;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.schedule.PlanScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanRunControllerTest {

	@Test
	void startsAndReadsApprovedPlanRun() throws Exception {
		PlanScheduler scheduler = mock(PlanScheduler.class);
		Plan plan = new Plan("plan-1", 1, "Goal", PlanStatus.APPROVED, List.of(), List.of(),
			null, Instant.now());
		PlanRun run = new PlanRun("run-1", "approval-1", plan, List.of(), Instant.now());
		when(scheduler.start("approval-1")).thenReturn(run);
		when(scheduler.get("run-1")).thenReturn(run);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new PlanRunController(scheduler)).build();

		mvc.perform(post("/api/plan-runs").contentType("application/json")
				.content("{\"approvalId\":\"approval-1\"}"))
			.andExpect(status().isAccepted())
			.andExpect(header().string("Location", "/api/plan-runs/run-1"))
			.andExpect(jsonPath("$.id").value("run-1"));
		mvc.perform(get("/api/plan-runs/run-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.approvalId").value("approval-1"));
	}
}
