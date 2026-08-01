package com.aidevos.orchestrator.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.dashboard.DashboardService;
import com.aidevos.orchestrator.dashboard.DashboardSummary;
import com.aidevos.orchestrator.dashboard.ExecutionStatistics;
import com.aidevos.orchestrator.dashboard.JobStatistics;
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
		MockMvc mockMvc = standaloneSetup(new DashboardController(service)).build();

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
}
