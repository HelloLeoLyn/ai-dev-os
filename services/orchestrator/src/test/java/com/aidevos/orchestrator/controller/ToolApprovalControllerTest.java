package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.job.JobService;
import com.aidevos.orchestrator.tool.approval.ToolApprovalRequest;
import com.aidevos.orchestrator.tool.approval.ToolApprovalService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ToolApprovalControllerTest {

	@Test
	void shouldListApproveAndResumeToolJob() throws Exception {
		ToolApprovalService service = mock(ToolApprovalService.class);
		JobService jobs = mock(JobService.class);
		ToolApprovalRequest request = request();
		when(service.getAll()).thenReturn(List.of(request));
		when(service.approve("tool-approval-1")).thenReturn(request);
		when(jobs.resumeAfterApproval("job-1")).thenReturn(true);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(
			new ToolApprovalController(service, jobs)).build();

		mvc.perform(get("/api/tool-approvals"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value("tool-approval-1"));
		mvc.perform(post("/api/tool-approvals/tool-approval-1/approve"))
			.andExpect(status().isOk());
	}

	private ToolApprovalRequest request() {
		return new ToolApprovalRequest("tool-approval-1", "execution-1", "invocation-1",
			"job-1", "filesystem", "write_file", "hash", "/workspace",
			"workspace-write", "write requested");
	}
}
