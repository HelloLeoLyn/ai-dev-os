package com.aidevos.orchestrator.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;
import com.aidevos.orchestrator.remote.RemoteBranchRecord;
import com.aidevos.orchestrator.remote.RemoteGitService;
import com.aidevos.orchestrator.remote.RemoteStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RemoteControllerTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private MockMvc mockMvc(RemoteGitService service) {
		return standaloneSetup(new RemoteController(service))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void shouldPushCommit() throws Exception {
		RemoteGitService service = mock(RemoteGitService.class);
		when(service.push(eq("commit-1"), isNull())).thenReturn(successfulRecord());
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(post("/api/commits/commit-1/push"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.remoteId").value("remote-1"))
			.andExpect(jsonPath("$.commitId").value("commit-1"))
			.andExpect(jsonPath("$.remote").value("origin"))
			.andExpect(jsonPath("$.status").value("SUCCESS"));
	}

	@Test
	void shouldPushToNamedRemote() throws Exception {
		RemoteGitService service = mock(RemoteGitService.class);
		when(service.push("commit-1", "upstream")).thenReturn(successfulRecord());
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(post("/api/commits/commit-1/push")
				.contentType("application/json")
				.content("{\"remote\":\"upstream\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.remote").value("origin"));
	}

	@Test
	void shouldReturnRemoteById() throws Exception {
		RemoteGitService service = mock(RemoteGitService.class);
		when(service.get("remote-1")).thenReturn(Optional.of(successfulRecord()));
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/remotes/remote-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.taskId").value("task-1"))
			.andExpect(jsonPath("$.branch").value("main"))
			.andExpect(jsonPath("$.url").value("file:///tmp/bare.git"));
	}

	@Test
	void shouldReturnRemotesByTask() throws Exception {
		RemoteGitService service = mock(RemoteGitService.class);
		when(service.getByTask("task-1")).thenReturn(List.of(successfulRecord()));
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/tasks/task-1/remotes"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].remoteId").value("remote-1"))
			.andExpect(jsonPath("$[0].status").value("SUCCESS"));
	}

	@Test
	void shouldReturn404ForMissingRemote() throws Exception {
		RemoteGitService service = mock(RemoteGitService.class);
		when(service.get("missing")).thenReturn(Optional.empty());
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/remotes/missing"))
			.andExpect(status().isNotFound());
	}

	private RemoteBranchRecord successfulRecord() {
		RemoteBranchRecord record = new RemoteBranchRecord("remote-1", "task-1", "workspace-1",
			"commit-1", "main", "origin", "file:///tmp/bare.git", NOW);
		record.markPushing();
		record.markSuccess();
		return record;
	}
}
