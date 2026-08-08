package com.aidevos.orchestrator.controller;

import java.time.Instant;
import java.util.Optional;

import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;
import com.aidevos.orchestrator.repair.FailureContext;
import com.aidevos.orchestrator.repair.RepairCoordinator;
import com.aidevos.orchestrator.repair.RepairStatus;
import com.aidevos.orchestrator.repair.RepairTask;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RepairControllerTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private MockMvc mockMvc(RepairCoordinator coordinator) {
		return standaloneSetup(new RepairController(coordinator))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void shouldStartRepair() throws Exception {
		RepairCoordinator coordinator = mock(RepairCoordinator.class);
		when(coordinator.start("task-1")).thenReturn(successfulRepair());
		MockMvc mockMvc = mockMvc(coordinator);

		mockMvc.perform(post("/api/repair/task-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.taskId").value("task-1"))
			.andExpect(jsonPath("$.status").value("SUCCESS"))
			.andExpect(jsonPath("$.retryCount").value(1))
			.andExpect(jsonPath("$.failureContext.testId").value("test-1"))
			.andExpect(jsonPath("$.lastResult").value("Repair succeeded after 1 attempt(s)"));
	}

	@Test
	void shouldReturnRepairStatus() throws Exception {
		RepairCoordinator coordinator = mock(RepairCoordinator.class);
		when(coordinator.get("task-1")).thenReturn(Optional.of(successfulRepair()));
		MockMvc mockMvc = mockMvc(coordinator);

		mockMvc.perform(get("/api/repair/task-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCESS"))
			.andExpect(jsonPath("$.retryCount").value(1))
			.andExpect(jsonPath("$.failureContext.errorMessage").value("exit code 1"))
			.andExpect(jsonPath("$.lastResult").isNotEmpty());
	}

	@Test
	void shouldReturn404ForMissingRepair() throws Exception {
		RepairCoordinator coordinator = mock(RepairCoordinator.class);
		when(coordinator.get("missing")).thenReturn(Optional.empty());
		MockMvc mockMvc = mockMvc(coordinator);

		mockMvc.perform(get("/api/repair/missing"))
			.andExpect(status().isNotFound());
	}

	private RepairTask successfulRepair() {
		FailureContext context = new FailureContext("task-1", "workspace-1", "test-1",
			"exit code 1", "BUILD FAILURE", "BUILD FAILURE", "1 file changed", NOW);
		RepairTask task = new RepairTask("repair-1", "task-1", "workspace-1", context);
		task.incrementRetry();
		task.markSuccess("Repair succeeded after 1 attempt(s)");
		return task;
	}
}
