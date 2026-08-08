package com.aidevos.orchestrator.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.change.ChangeStatus;
import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ChangeControllerTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private MockMvc mockMvc(ChangeService service) {
		return standaloneSetup(new ChangeController(service))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void shouldListChangesByTask() throws Exception {
		ChangeService service = mock(ChangeService.class);
		when(service.getChangesByTask("task-1")).thenReturn(List.of(change()));
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/tasks/task-1/changes"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].changeId").value("change-1"))
			.andExpect(jsonPath("$[0].status").value("CREATED"))
			.andExpect(jsonPath("$[0].filesChanged").value(3));
	}

	@Test
	void shouldReturnChangeById() throws Exception {
		ChangeService service = mock(ChangeService.class);
		when(service.getChange("change-1")).thenReturn(Optional.of(change()));
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/changes/change-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.taskId").value("task-1"))
			.andExpect(jsonPath("$.workspaceId").value("workspace-1"))
			.andExpect(jsonPath("$.executionId").value("exec-1"));
	}

	@Test
	void shouldReturnDiffAsPlainText() throws Exception {
		ChangeService service = mock(ChangeService.class);
		when(service.getDiff("change-1")).thenReturn("diff --git a/a.txt b/a.txt\n@@ -1 +1,2 @@\n");
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/changes/change-1/diff"))
			.andExpect(status().isOk())
			.andExpect(content().string("diff --git a/a.txt b/a.txt\n@@ -1 +1,2 @@\n"));
	}

	@Test
	void shouldStartReview() throws Exception {
		ChangeService service = mock(ChangeService.class);
		ChangeSet reviewing = reviewed();
		when(service.startReview("change-1")).thenReturn(reviewing);
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(post("/api/changes/change-1/review"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REVIEWING"));
	}

	@Test
	void shouldApproveWithReviewer() throws Exception {
		ChangeService service = mock(ChangeService.class);
		ChangeSet approved = approved();
		when(service.approve(eq("change-1"), eq("user-1"))).thenReturn(approved);
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(post("/api/changes/change-1/approve")
				.contentType("application/json")
				.content("{\"reviewer\":\"user-1\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.reviewedBy").value("user-1"));
	}

	@Test
	void shouldApproveWithoutReviewer() throws Exception {
		ChangeService service = mock(ChangeService.class);
		when(service.approve(eq("change-1"), isNull())).thenReturn(approved());
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(post("/api/changes/change-1/approve"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPROVED"));
	}

	@Test
	void shouldRejectChange() throws Exception {
		ChangeService service = mock(ChangeService.class);
		ChangeSet rejected = rejected();
		when(service.reject(eq("change-1"), isNull())).thenReturn(rejected);
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(post("/api/changes/change-1/reject"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJECTED"));
	}

	@Test
	void shouldReturn404ForMissingChange() throws Exception {
		ChangeService service = mock(ChangeService.class);
		when(service.getChange("missing")).thenReturn(Optional.empty());
		MockMvc mockMvc = mockMvc(service);

		mockMvc.perform(get("/api/changes/missing"))
			.andExpect(status().isNotFound());
	}

	private ChangeSet change() {
		return new ChangeSet("change-1", "task-1", "workspace-1", "project-a", "exec-1",
			"main", "diff --git a/a.txt b/a.txt\n", "1 file changed", 3, 5, 1, 1, 2, 0, NOW);
	}

	private ChangeSet reviewed() {
		ChangeSet change = change();
		change.markReviewing();
		return change;
	}

	private ChangeSet approved() {
		ChangeSet change = reviewed();
		change.markApproved("user-1");
		return change;
	}

	private ChangeSet rejected() {
		ChangeSet change = reviewed();
		change.markRejected("user-2");
		return change;
	}
}
