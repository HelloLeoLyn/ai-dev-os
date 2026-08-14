package com.aidevos.orchestrator.controller;

import java.time.Instant;
import java.util.List;
import com.aidevos.orchestrator.backlog.*;
import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class BacklogControllerTest {
	@Test void supportsCrudStatusAndFilters() throws Exception {
		BacklogService service = mock(BacklogService.class); BacklogItem item = item();
		when(service.create(any())).thenReturn(item); when(service.get("backlog-1")).thenReturn(item);
		when(service.list(eq(BacklogStatus.READY), isNull(), isNull(), isNull())).thenReturn(List.of(item));
		when(service.update(eq("backlog-1"), any())).thenReturn(item); when(service.changeStatus(eq("backlog-1"), any())).thenReturn(item);
		MockMvc mvc = mvc(service);
		mvc.perform(post("/api/backlog").contentType("application/json").content("{\"title\":\"Work\"}"))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.backlogItemId").value("backlog-1"));
		mvc.perform(get("/api/backlog?status=READY")).andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("READY"));
		mvc.perform(get("/api/backlog/backlog-1")).andExpect(status().isOk());
		mvc.perform(put("/api/backlog/backlog-1").contentType("application/json").content("{\"title\":\"Work\"}"))
			.andExpect(status().isOk());
		mvc.perform(post("/api/backlog/backlog-1/status").contentType("application/json").content("{\"status\":\"BLOCKED\",\"blockedReason\":\"wait\"}"))
			.andExpect(status().isOk());
	}

	@Test void convertsAndReturns404OrBadRequest() throws Exception {
		BacklogService service = mock(BacklogService.class); BacklogItem item = item();
		com.aidevos.orchestrator.taskcenter.TaskRecord task = new com.aidevos.orchestrator.taskcenter.TaskRecord("task-1", "Work", null);
		when(service.convertToTask(eq("backlog-1"), any())).thenReturn(new BacklogConversionResult(item, task));
		when(service.get("missing")).thenThrow(new ResourceNotFoundException("BacklogItem", "missing"));
		when(service.changeStatus(eq("backlog-1"), any())).thenThrow(new IllegalArgumentException("Invalid transition"));
		MockMvc mvc = mvc(service);
		mvc.perform(post("/api/backlog/backlog-1/convert-to-task").contentType("application/json").content("{\"goal\":\"Goal\",\"projectId\":\"p\",\"workspaceId\":\"w\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.task.taskId").value("task-1"));
		mvc.perform(get("/api/backlog/missing")).andExpect(status().isNotFound());
		mvc.perform(post("/api/backlog/backlog-1/status").contentType("application/json").content("{\"status\":\"DONE\"}"))
			.andExpect(status().isBadRequest());
	}

	private MockMvc mvc(BacklogService service) { return standaloneSetup(new BacklogController(service)).setControllerAdvice(new GlobalExceptionHandler()).build(); }
	private BacklogItem item() { return new BacklogItem("backlog-1", "Work", null, BacklogStatus.READY, BacklogPriority.HIGH, null, null, BacklogSourceType.ROADMAP, "docs/roadmap/README.md#work", List.of(), List.of(), Instant.now()); }
}
