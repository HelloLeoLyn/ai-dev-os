package com.aidevos.orchestrator.metrics;

import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class MetricsControllerTest {

	@Test
	void shouldReturnOperationalMetrics() throws Exception {
		MetricsService service = mock(MetricsService.class);
		when(service.collect()).thenReturn(new MetricsSnapshot(6, 12, 2, 1, 3, 8, 4));
		MockMvc mockMvc = standaloneSetup(new MetricsController(service))
			.setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(get("/api/metrics"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.agents").value(6))
			.andExpect(jsonPath("$.tasks").value(12))
			.andExpect(jsonPath("$.runningJobs").value(2))
			.andExpect(jsonPath("$.failedJobs").value(1))
			.andExpect(jsonPath("$.recoveryJobs").value(3))
			.andExpect(jsonPath("$.memoryRecords").value(8))
			.andExpect(jsonPath("$.plugins").value(4));
	}
}
