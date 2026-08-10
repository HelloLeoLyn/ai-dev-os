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
	void shouldReturnRepairByCiRun() throws Exception {
		RepairCoordinator coordinator = mock(RepairCoordinator.class);
		when(coordinator.getByCiRun("ci-1")).thenReturn(Optional.of(ciRepair()));
		MockMvc mockMvc = mockMvc(coordinator);

		mockMvc.perform(get("/api/repair/ci/ci-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCESS"))
			.andExpect(jsonPath("$.failureContext.sourceType").value("CI_FAILURE"))
			.andExpect(jsonPath("$.failureContext.sourceId").value("ci-1"))
			.andExpect(jsonPath("$.failureContext.commitHash").value("abc123def"))
			.andExpect(jsonPath("$.failureContext.branch").value("main"))
			.andExpect(jsonPath("$.failureContext.changedFiles").value(2));
	}

	@Test
	void shouldReturn404ForMissingCiRepair() throws Exception {
		RepairCoordinator coordinator = mock(RepairCoordinator.class);
		when(coordinator.getByCiRun("missing")).thenReturn(Optional.empty());
		MockMvc mockMvc = mockMvc(coordinator);

		mockMvc.perform(get("/api/repair/ci/missing"))
			.andExpect(status().isNotFound());
	}

	@Test
	void shouldReturn404ForMissingRepair() throws Exception {
		RepairCoordinator coordinator = mock(RepairCoordinator.class);
		when(coordinator.get("missing")).thenReturn(Optional.empty());
		MockMvc mockMvc = mockMvc(coordinator);

		mockMvc.perform(get("/api/repair/missing"))
			.andExpect(status().isNotFound());
	}

	private RepairTask ciRepair() {
		FailureContext context = new FailureContext("task-1", "workspace-1", null,
			"CI run failed: pipeline-1", null, "https://mock.dev/ci/pipeline-1",
			"2 files changed, 4 insertions(+), 2 deletions(-)",
			"CI_FAILURE", "ci-1", "abc123def", "main", 2, NOW);
		RepairTask task = new RepairTask("repair-ci-1", "task-1", "workspace-1", context);
		task.markSuccess("Repair succeeded after 1 attempt(s)");
		return task;
	}

	private RepairTask successfulRepair() {
		FailureContext context = new FailureContext("task-1", "workspace-1", "test-1",
			"exit code 1", "BUILD FAILURE", "BUILD FAILURE", "1 file changed",
			"TEST_FAILURE", "test-1", "", "", 0, NOW);
		RepairTask task = new RepairTask("repair-1", "task-1", "workspace-1", context);
		task.incrementRetry();
		task.markSuccess("Repair succeeded after 1 attempt(s)");
		return task;
	}
}
