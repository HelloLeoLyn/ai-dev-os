package com.aidevos.orchestrator.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.commit.CommitRecord;
import com.aidevos.orchestrator.commit.CommitService;
import com.aidevos.orchestrator.commit.CommitStatus;
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

class CommitControllerTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private MockMvc mockMvc(CommitService service) {
		return standaloneSetup(new CommitController(service))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void shouldCommitApprovedChange() throws Exception {
		CommitService service = mock(CommitService.class);
		when(service.commit("change-1")).thenReturn(successfulCommit());
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(post("/api/changes/change-1/commit"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.commitId").value("commit-1"))
			.andExpect(jsonPath("$.changeId").value("change-1"))
			.andExpect(jsonPath("$.status").value("SUCCESS"))
			.andExpect(jsonPath("$.gitHash").value("abc123def"));
	}

	@Test
	void shouldReturnCommitById() throws Exception {
		CommitService service = mock(CommitService.class);
		when(service.getCommit("commit-1")).thenReturn(Optional.of(successfulCommit()));
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/commits/commit-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.taskId").value("task-1"))
			.andExpect(jsonPath("$.branch").value("main"))
			.andExpect(jsonPath("$.gitHash").value("abc123def"));
	}

	@Test
	void shouldReturnCommitsByTask() throws Exception {
		CommitService service = mock(CommitService.class);
		when(service.getCommitsByTask("task-1")).thenReturn(List.of(successfulCommit()));
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/tasks/task-1/commits"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].commitId").value("commit-1"))
			.andExpect(jsonPath("$[0].status").value("SUCCESS"));
	}

	@Test
	void shouldReturn404ForMissingCommit() throws Exception {
		CommitService service = mock(CommitService.class);
		when(service.getCommit("missing")).thenReturn(Optional.empty());
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/commits/missing"))
			.andExpect(status().isNotFound());
	}

	private CommitRecord successfulCommit() {
		CommitRecord record = new CommitRecord("commit-1", "change-1", "task-1", "workspace-1",
			"main", "AI change change-1 for task task-1", NOW);
		record.markCommitting();
		record.markSuccess("abc123def");
		return record;
	}
}
