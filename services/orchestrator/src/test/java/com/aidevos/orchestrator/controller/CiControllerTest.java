package com.aidevos.orchestrator.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.ci.CiRunRecord;
import com.aidevos.orchestrator.ci.CiService;
import com.aidevos.orchestrator.ci.CiStatus;
import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CiControllerTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private MockMvc mockMvc(CiService service) {
		return standaloneSetup(new CiController(service))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void shouldReturnCiRunById() throws Exception {
		CiService service = mock(CiService.class);
		when(service.get("ci-1")).thenReturn(Optional.of(successfulRun()));
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/ci/ci-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ciRunId").value("ci-1"))
			.andExpect(jsonPath("$.taskId").value("task-1"))
			.andExpect(jsonPath("$.pullRequestId").value("pr-1"))
			.andExpect(jsonPath("$.provider").value("mock"))
			.andExpect(jsonPath("$.pipelineId").value("pipeline-1"))
			.andExpect(jsonPath("$.status").value("SUCCESS"));
	}

	@Test
	void shouldReturnCiRunsByTask() throws Exception {
		CiService service = mock(CiService.class);
		when(service.getByTask("task-1")).thenReturn(List.of(successfulRun()));
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/tasks/task-1/ci"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].ciRunId").value("ci-1"))
			.andExpect(jsonPath("$[0].status").value("SUCCESS"));
	}

	@Test
	void shouldCheckPullRequestCi() throws Exception {
		CiService service = mock(CiService.class);
		when(service.check("pr-1")).thenReturn(successfulRun());
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(post("/api/pull-requests/pr-1/ci/check"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ciRunId").value("ci-1"))
			.andExpect(jsonPath("$.commitHash").value("abc123def"))
			.andExpect(jsonPath("$.status").value("SUCCESS"));
	}

	@Test
	void shouldReturn404ForMissingCiRun() throws Exception {
		CiService service = mock(CiService.class);
		when(service.get("missing")).thenReturn(Optional.empty());
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/ci/missing"))
			.andExpect(status().isNotFound());
	}

	private CiRunRecord successfulRun() {
		CiRunRecord record = new CiRunRecord("ci-1", "task-1", "pr-1", "mock", "main",
			"abc123def", NOW);
		record.updatePipelineId("pipeline-1");
		record.updateReportUrl("https://mock.dev/ci/pipeline-1");
		record.markRunning();
		record.markSuccess();
		return record;
	}
}
