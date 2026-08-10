package com.aidevos.orchestrator.controller;

import java.time.Instant;
import java.util.Optional;

import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;
import com.aidevos.orchestrator.pr.PullRequestCreateRequest;
import com.aidevos.orchestrator.pr.PullRequestRecord;
import com.aidevos.orchestrator.pr.PullRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class PullRequestControllerTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private MockMvc mockMvc(PullRequestService service) {
		return standaloneSetup(new PullRequestController(service))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void shouldCreatePullRequestForCommit() throws Exception {
		PullRequestService service = mock(PullRequestService.class);
		when(service.createPullRequest(eq("commit-1"), isNull())).thenReturn(openedRecord());
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(post("/api/commits/commit-1/pull-request"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pullRequestId").value("pr-1"))
			.andExpect(jsonPath("$.commitId").value("commit-1"))
			.andExpect(jsonPath("$.taskId").value("task-1"))
			.andExpect(jsonPath("$.branch").value("main"))
			.andExpect(jsonPath("$.targetBranch").value("main"))
			.andExpect(jsonPath("$.url").value("https://mock.dev/pr/pr-1"))
			.andExpect(jsonPath("$.status").value("OPEN"));
	}

	@Test
	void shouldCreatePullRequestWithOverrides() throws Exception {
		PullRequestService service = mock(PullRequestService.class);
		when(service.createPullRequest(eq("commit-1"), any(PullRequestCreateRequest.class)))
			.thenReturn(openedRecord());
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(post("/api/commits/commit-1/pull-request")
				.contentType("application/json")
				.content("{\"targetBranch\":\"develop\",\"title\":\"My PR\",\"description\":\"desc\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("OPEN"));
	}

	@Test
	void shouldReturnPullRequestById() throws Exception {
		PullRequestService service = mock(PullRequestService.class);
		when(service.get("pr-1")).thenReturn(Optional.of(openedRecord()));
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/pull-requests/pr-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pullRequestId").value("pr-1"))
			.andExpect(jsonPath("$.commitId").value("commit-1"))
			.andExpect(jsonPath("$.status").value("OPEN"));
	}

	@Test
	void shouldClosePullRequest() throws Exception {
		PullRequestService service = mock(PullRequestService.class);
		when(service.close("pr-1")).thenReturn(closedRecord());
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(post("/api/pull-requests/pr-1/close"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CLOSED"));
	}

	@Test
	void shouldMergePullRequest() throws Exception {
		PullRequestService service = mock(PullRequestService.class);
		when(service.merge("pr-1")).thenReturn(mergedRecord());
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(post("/api/pull-requests/pr-1/merge"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("MERGED"));
	}

	@Test
	void shouldReturn404ForMissingPullRequest() throws Exception {
		PullRequestService service = mock(PullRequestService.class);
		when(service.get("missing")).thenReturn(Optional.empty());
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/pull-requests/missing"))
			.andExpect(status().isNotFound());
	}

	private PullRequestRecord openedRecord() {
		PullRequestRecord record = new PullRequestRecord("pr-1", "task-1", "commit-1", "remote-1",
			"main", "main", "AI change for task task-1",
			"Auto-generated pull request by AI Dev OS", "https://mock.dev/pr/pr-1", NOW);
		record.markOpened();
		return record;
	}

	private PullRequestRecord closedRecord() {
		PullRequestRecord record = openedRecord();
		record.markClosed();
		return record;
	}

	private PullRequestRecord mergedRecord() {
		PullRequestRecord record = openedRecord();
		record.markMerged();
		return record;
	}
}
