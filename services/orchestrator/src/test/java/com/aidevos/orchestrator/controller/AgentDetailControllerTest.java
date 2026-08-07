package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.aidevos.orchestrator.dashboard.AgentDetailDTO;
import com.aidevos.orchestrator.dashboard.AgentExecutionSummary;
import com.aidevos.orchestrator.dashboard.AgentHistoryDTO;
import com.aidevos.orchestrator.dashboard.AgentRegistryService;
import com.aidevos.orchestrator.dashboard.AgentRuntimeStatus;
import com.aidevos.orchestrator.manager.AgentManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentDetailControllerTest {

	private MockMvc mockMvc(AgentRegistryService registryService) {
		return standaloneSetup(
			new AgentController(new AgentManager(), registryService)).setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	@Test
	void shouldReturnAgentDetail() throws Exception {
		AgentRegistryService service = mock(AgentRegistryService.class);
		AgentDetailDTO detail = new AgentDetailDTO("main", "tester", "system",
			AgentRuntimeStatus.ONLINE, List.of("testing", "browser"),
			Map.of("agentId", "main"), Instant.parse("2026-08-01T00:00:00Z"),
			List.of(new AgentExecutionSummary("exec-1", "job-1", "SUCCESS",
				Instant.parse("2026-08-01T00:00:00Z"),
				Instant.parse("2026-08-01T00:01:00Z"), "ok")));
		when(service.getAgentDetail("main")).thenReturn(Optional.of(detail));

		mockMvc(service).perform(get("/api/agents/main"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.agentId").value("main"))
			.andExpect(jsonPath("$.name").value("tester"))
			.andExpect(jsonPath("$.type").value("system"))
			.andExpect(jsonPath("$.status").value("ONLINE"))
			.andExpect(jsonPath("$.capabilities[0]").value("testing"))
			.andExpect(jsonPath("$.configuration.agentId").value("main"))
			.andExpect(jsonPath("$.lastActivity").value("2026-08-01T00:00:00Z"))
			.andExpect(jsonPath("$.executions[0].executionId").value("exec-1"))
			.andExpect(jsonPath("$.executions[0].status").value("SUCCESS"));
	}

	@Test
	void shouldReturnAgentHistory() throws Exception {
		AgentRegistryService service = mock(AgentRegistryService.class);
		AgentHistoryDTO history = new AgentHistoryDTO(
			List.of(new AgentExecutionSummary("exec-1", "job-1", "FAILED",
				Instant.parse("2026-08-01T00:00:00Z"), null, "boom")),
			1, 1, "boom");
		when(service.getAgentHistory("tester")).thenReturn(Optional.of(history));

		mockMvc(service).perform(get("/api/agents/tester/history"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.recentExecutions[0].executionId").value("exec-1"))
			.andExpect(jsonPath("$.successCount").value(1))
			.andExpect(jsonPath("$.failedCount").value(1))
			.andExpect(jsonPath("$.lastError").value("boom"));
	}

	@Test
	void shouldReturnNotFoundForUnknownAgent() throws Exception {
		AgentRegistryService service = mock(AgentRegistryService.class);
		when(service.getAgentDetail("missing")).thenReturn(Optional.empty());
		when(service.getAgentHistory("missing")).thenReturn(Optional.empty());

		mockMvc(service).perform(get("/api/agents/missing"))
			.andExpect(status().isNotFound());
		mockMvc(service).perform(get("/api/agents/missing/history"))
			.andExpect(status().isNotFound());
	}
}
