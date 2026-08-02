package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.approval.CodingApprovalRequest;
import com.aidevos.orchestrator.approval.CodingApprovalService;
import com.aidevos.orchestrator.job.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApprovalControllerTest {

	@Test
	void shouldListAndApproveJobRequest() throws Exception {
		CodingApprovalService approvalService = mock(CodingApprovalService.class);
		JobService jobService = mock(JobService.class);
		CodingApprovalRequest request = request("approval-1", "job-1");
		when(approvalService.getAll()).thenReturn(List.of(request));
		when(approvalService.approve("approval-1")).thenReturn(request);
		when(jobService.resumeAfterApproval("job-1")).thenReturn(true);
		MockMvc mvc = mvc(approvalService, jobService);

		mvc.perform(get("/api/approvals"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value("approval-1"));
		mvc.perform(post("/api/approvals/approval-1/approve"))
			.andExpect(status().isOk());
	}

	@Test
	void shouldReturnConflictWhenApprovedJobCannotBeRequeued() throws Exception {
		CodingApprovalService approvalService = mock(CodingApprovalService.class);
		JobService jobService = mock(JobService.class);
		CodingApprovalRequest request = request("approval-1", "job-1");
		when(approvalService.approve("approval-1")).thenReturn(request);
		when(jobService.resumeAfterApproval("job-1")).thenReturn(false);

		mvc(approvalService, jobService).perform(post("/api/approvals/approval-1/approve"))
			.andExpect(status().isConflict());
	}

	@Test
	void shouldReturnNotFoundForUnknownApproval() throws Exception {
		mvc(mock(CodingApprovalService.class), mock(JobService.class))
			.perform(post("/api/approvals/missing/approve"))
			.andExpect(status().isNotFound());
	}

	private MockMvc mvc(CodingApprovalService approvalService, JobService jobService) {
		return MockMvcBuilders.standaloneSetup(new ApprovalController(approvalService, jobService)).build();
	}

	private CodingApprovalRequest request(String id, String jobId) {
		return new CodingApprovalRequest(id, "task-1", jobId, "/workspace",
			"workspace-write", "reason");
	}
}
