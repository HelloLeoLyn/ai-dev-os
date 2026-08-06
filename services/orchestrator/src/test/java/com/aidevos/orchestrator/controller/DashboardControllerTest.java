package com.aidevos.orchestrator.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.dashboard.DashboardQueryService;
import com.aidevos.orchestrator.dashboard.DashboardService;
import com.aidevos.orchestrator.dashboard.DashboardSummary;
import com.aidevos.orchestrator.dashboard.DashboardSummaryDTO;
import com.aidevos.orchestrator.dashboard.DashboardTimeline;
import com.aidevos.orchestrator.dashboard.ExecutionStatistics;
import com.aidevos.orchestrator.dashboard.ExecutionSummaryDTO;
import com.aidevos.orchestrator.dashboard.JobStatistics;
import com.aidevos.orchestrator.dashboard.JobSummaryDTO;
import com.aidevos.orchestrator.dashboard.TaskStatistics;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardControllerTest {

	@Test
	void shouldReturnDashboardJson() throws Exception {
		DashboardService service = mock(DashboardService.class);
		DashboardSummary summary = new DashboardSummary(Instant.parse("2026-08-01T00:00:00Z"),
			new TaskStatistics(3, Map.of("pending", 3L)),
			new JobStatistics(4, 1, 1, 1, 1, 50.0),
			new ExecutionStatistics(2, 1, 1, 0, 50.0), List.of());
		when(service.getSummary()).thenReturn(summary);
		MockMvc mockMvc = standaloneSetup(
			new DashboardController(service, mock(DashboardQueryService.class))).build();

		mockMvc.perform(get("/api/dashboard"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.generatedAt").value("2026-08-01T00:00:00Z"))
			.andExpect(jsonPath("$.tasks.total").value(3))
			.andExpect(jsonPath("$.tasks.byStatus.pending").value(3))
			.andExpect(jsonPath("$.jobs.succeeded").value(1))
			.andExpect(jsonPath("$.jobs.successRate").value(50.0))
			.andExpect(jsonPath("$.executions.total").value(2))
			.andExpect(jsonPath("$.recentJobs").isEmpty());

		verify(service).getSummary();
	}

	@Test
	void shouldReturnDashboardSummaryJson() throws Exception {
		DashboardService service = mock(DashboardService.class);
		DashboardSummaryDTO dto = new DashboardSummaryDTO(
			new DashboardSummaryDTO.Health("UP", true),
			new DashboardSummaryDTO.Agents(2, 2),
			new JobStatistics(4, 1, 1, 1, 1, 50.0),
			new ExecutionStatistics(2, 1, 1, 0, 50.0),
			new DashboardSummaryDTO.Recovery(1));
		when(service.getDashboardSummary()).thenReturn(dto);
		MockMvc mockMvc = standaloneSetup(
			new DashboardController(service, mock(DashboardQueryService.class))).build();

		mockMvc.perform(get("/api/dashboard/summary"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.health.status").value("UP"))
			.andExpect(jsonPath("$.health.ready").value(true))
			.andExpect(jsonPath("$.agents.total").value(2))
			.andExpect(jsonPath("$.agents.enabled").value(2))
			.andExpect(jsonPath("$.jobs.total").value(4))
			.andExpect(jsonPath("$.jobs.running").value(1))
			.andExpect(jsonPath("$.executions.total").value(2))
			.andExpect(jsonPath("$.recovery.pending").value(1));

		verify(service).getDashboardSummary();
	}

	@Test
	void shouldReturnDashboardJobs() throws Exception {
		DashboardQueryService queryService = mock(DashboardQueryService.class);
		when(queryService.listJobs()).thenReturn(List.of(new JobSummaryDTO("job-1", "RUNNING",
			5, "worker-1", Instant.parse("2026-08-01T00:00:00Z"),
			Instant.parse("2026-08-01T00:01:00Z"))));
		MockMvc mockMvc = standaloneSetup(
			new DashboardController(mock(DashboardService.class), queryService)).build();

		mockMvc.perform(get("/api/dashboard/jobs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].jobId").value("job-1"))
			.andExpect(jsonPath("$[0].status").value("RUNNING"))
			.andExpect(jsonPath("$[0].priority").value(5))
			.andExpect(jsonPath("$[0].leaseOwner").value("worker-1"))
			.andExpect(jsonPath("$[0].createdAt").value("2026-08-01T00:00:00Z"))
			.andExpect(jsonPath("$[0].updatedAt").value("2026-08-01T00:01:00Z"));

		verify(queryService).listJobs();
	}

	@Test
	void shouldReturnDashboardExecutions() throws Exception {
		DashboardQueryService queryService = mock(DashboardQueryService.class);
		when(queryService.listExecutions()).thenReturn(List.of(
			new ExecutionSummaryDTO("exec-1", "job-1", "FAILED", 2, "STALE_EXECUTION",
				Instant.parse("2026-08-01T00:00:00Z"))));
		MockMvc mockMvc = standaloneSetup(
			new DashboardController(mock(DashboardService.class), queryService)).build();

		mockMvc.perform(get("/api/dashboard/executions"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].executionId").value("exec-1"))
			.andExpect(jsonPath("$[0].jobId").value("job-1"))
			.andExpect(jsonPath("$[0].status").value("FAILED"))
			.andExpect(jsonPath("$[0].attempt").value(2))
			.andExpect(jsonPath("$[0].failureReason").value("STALE_EXECUTION"))
			.andExpect(jsonPath("$[0].createdAt").value("2026-08-01T00:00:00Z"));

		verify(queryService).listExecutions();
	}

	@Test
	void shouldReturnDashboardTimeline() throws Exception {
		DashboardQueryService queryService = mock(DashboardQueryService.class);
		when(queryService.timeline("job-1")).thenReturn(
			new DashboardTimeline("JOB", "job-1", List.of()));
		MockMvc mockMvc = standaloneSetup(
			new DashboardController(mock(DashboardService.class), queryService)).build();

		mockMvc.perform(get("/api/dashboard/timeline/job-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scopeType").value("JOB"))
			.andExpect(jsonPath("$.scopeId").value("job-1"))
			.andExpect(jsonPath("$.events").isEmpty());

		verify(queryService).timeline("job-1");
	}
}
