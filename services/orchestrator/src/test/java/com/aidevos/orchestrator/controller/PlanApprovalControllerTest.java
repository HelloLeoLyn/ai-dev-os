package com.aidevos.orchestrator.controller;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanApprovalControllerTest {

	@Test
	void shouldListAndApproveWithoutExecutingPlan() throws Exception {
		PlanApprovalService service = mock(PlanApprovalService.class);
		PlanApprovalRequest approval = approval();
		when(service.getAll()).thenReturn(List.of(approval));
		when(service.approve("approval-1", "alice")).thenReturn(approval);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(
			new PlanApprovalController(service)).build();

		mvc.perform(get("/api/plan-approvals"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].planId").value("plan-1"));
		mvc.perform(post("/api/plan-approvals/approval-1/approve")
				.contentType("application/json")
				.content("{\"approver\":\"alice\"}"))
			.andExpect(status().isOk());
	}

	private PlanApprovalRequest approval() {
		Plan plan = new Plan("plan-1", 1, "Goal", PlanStatus.DRAFT, List.of(), List.of(),
			null, Instant.now());
		return new PlanApprovalRequest("approval-1", "request-1", plan, "hash", Instant.now());
	}
}
