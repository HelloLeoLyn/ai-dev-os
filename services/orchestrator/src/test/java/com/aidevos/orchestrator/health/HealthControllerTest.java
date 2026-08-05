package com.aidevos.orchestrator.health;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class HealthControllerTest {

	@Test
	void livenessIsAlwaysUp() throws Exception {
		HealthController controller = new HealthController(mock(ReadinessGate.class));
		MockMvc mockMvc = standaloneSetup(controller).build();

		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void readinessFailsWith503WhileGateIsNotReady() throws Exception {
		ReadinessGate gate = mock(ReadinessGate.class);
		when(gate.isReady()).thenReturn(false);
		when(gate.details()).thenReturn(Map.of("startupComplete", false));
		MockMvc mockMvc = standaloneSetup(new HealthController(gate)).build();

		mockMvc.perform(get("/api/health/readiness"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.status").value("NOT_READY"))
			.andExpect(jsonPath("$.details.startupComplete").value(false));
	}

	@Test
	void readinessPassesWith200OnceGateIsReady() throws Exception {
		ReadinessGate gate = mock(ReadinessGate.class);
		when(gate.isReady()).thenReturn(true);
		when(gate.details()).thenReturn(Map.of("startupComplete", true));
		MockMvc mockMvc = standaloneSetup(new HealthController(gate)).build();

		mockMvc.perform(get("/api/health/readiness"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("READY"))
			.andExpect(jsonPath("$.details.startupComplete").value(true));
	}
}
