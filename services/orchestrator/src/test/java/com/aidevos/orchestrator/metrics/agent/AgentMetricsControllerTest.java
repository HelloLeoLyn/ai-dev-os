package com.aidevos.orchestrator.metrics.agent;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AgentMetricsControllerTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private MockMvc mockMvc(AgentMetricsService service) {
		return standaloneSetup(new AgentMetricsController(service))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void shouldReturnAgentRanking() throws Exception {
		AgentMetricsService service = mock(AgentMetricsService.class);
		when(service.listAgentMetrics()).thenReturn(List.of(
			metrics("coder", 3, 2, 1, 4000, 1),
			metrics("tester", 1, 1, 0, 2000, 0)));
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/metrics/agents"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].agentId").value("coder"))
			.andExpect(jsonPath("$[0].taskCount").value(3))
			.andExpect(jsonPath("$[0].successCount").value(2))
			.andExpect(jsonPath("$[0].repairCount").value(1))
			.andExpect(jsonPath("$[1].agentId").value("tester"));
	}

	@Test
	void shouldReturnAgentDetail() throws Exception {
		AgentMetricsService service = mock(AgentMetricsService.class);
		when(service.getAgentDetail("coder")).thenReturn(new AgentMetricsDetail(
			metrics("coder", 1, 1, 0, 1000, 0),
			List.of(new AgentExecutionMetric("task-1", "coder", "exec-1", 1000, "SUCCESS", NOW))));
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/metrics/agents/coder"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.metrics.agentId").value("coder"))
			.andExpect(jsonPath("$.executions[0].executionId").value("exec-1"))
			.andExpect(jsonPath("$.executions[0].durationMillis").value(1000));
	}

	@Test
	void shouldReturnTaskMetrics() throws Exception {
		AgentMetricsService service = mock(AgentMetricsService.class);
		when(service.getTaskMetrics("task-1")).thenReturn(new TaskExecutionMetrics(
			"task-1", "COMPLETED", 2, 1, 1, 15000, 7500, 0, 0, 1, 1, 0, 1.0,
			List.of(new AgentExecutionMetric("task-1", "coder", "exec-1", 10000, "SUCCESS", NOW))));
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/metrics/tasks/task-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.taskId").value("task-1"))
			.andExpect(jsonPath("$.taskStatus").value("COMPLETED"))
			.andExpect(jsonPath("$.executionCount").value(2))
			.andExpect(jsonPath("$.reviewPassRate").value(1.0));
	}

	@Test
	void shouldReturn404ForUnknownAgent() throws Exception {
		AgentMetricsService service = mock(AgentMetricsService.class);
		when(service.getAgentDetail("missing"))
			.thenThrow(new ResourceNotFoundException("Agent", "missing"));
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/metrics/agents/missing"))
			.andExpect(status().isNotFound());
	}

	private AgentMetrics metrics(String agentId, int taskCount, int successCount,
			int failedCount, long averageDuration, int repairCount) {
		return new AgentMetrics(agentId, agentId, taskCount, successCount, failedCount, 0,
			averageDuration, NOW, repairCount, 0);
	}
}
