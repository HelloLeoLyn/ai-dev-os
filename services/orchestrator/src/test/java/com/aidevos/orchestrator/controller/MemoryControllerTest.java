package com.aidevos.orchestrator.controller;

import java.time.Instant;

import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.MemoryType;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class MemoryControllerTest {

	@Test
	void shouldCreateMemory() throws Exception {
		MemoryService service = mock(MemoryService.class);
		when(service.create(any())).thenReturn(record("mem-1"));
		MockMvc mockMvc = standaloneSetup(new MemoryController(service)).build();

		mockMvc.perform(post("/api/memory")
				.contentType("application/json")
				.content("{\"projectId\":\"project-a\",\"type\":\"PROJECT_RULE\","
					+ "\"key\":\"rule-1\",\"content\":\"keep API stable\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value("mem-1"))
			.andExpect(jsonPath("$.type").value("PROJECT_RULE"))
			.andExpect(jsonPath("$.content").value("keep API stable"));
	}

	@Test
	void shouldListMemories() throws Exception {
		MemoryService service = mock(MemoryService.class);
		when(service.list("project-a", MemoryType.BUG_RECORD)).thenReturn(
			java.util.List.of(record("mem-1")));
		MockMvc mockMvc = standaloneSetup(new MemoryController(service)).build();

		mockMvc.perform(get("/api/memory")
				.param("projectId", "project-a")
				.param("type", "BUG_RECORD"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value("mem-1"));

		verify(service).list("project-a", MemoryType.BUG_RECORD);
	}

	@Test
	void shouldGetMemoryById() throws Exception {
		MemoryService service = mock(MemoryService.class);
		when(service.get("mem-1")).thenReturn(record("mem-1"));
		MockMvc mockMvc = standaloneSetup(new MemoryController(service)).build();

		mockMvc.perform(get("/api/memory/mem-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("mem-1"));
	}

	@Test
	void shouldReturn404WhenMemoryMissing() throws Exception {
		MemoryService service = mock(MemoryService.class);
		when(service.get("missing")).thenReturn(null);
		MockMvc mockMvc = standaloneSetup(new MemoryController(service)).build();

		mockMvc.perform(get("/api/memory/missing"))
			.andExpect(status().isNotFound());
	}

	@Test
	void shouldDeleteMemory() throws Exception {
		MemoryService service = mock(MemoryService.class);
		when(service.delete("mem-1")).thenReturn(true);
		MockMvc mockMvc = standaloneSetup(new MemoryController(service)).build();

		mockMvc.perform(delete("/api/memory/mem-1"))
			.andExpect(status().isNoContent());
	}

	@Test
	void shouldReturn404WhenDeletingMissingMemory() throws Exception {
		MemoryService service = mock(MemoryService.class);
		when(service.delete("missing")).thenReturn(false);
		MockMvc mockMvc = standaloneSetup(new MemoryController(service)).build();

		mockMvc.perform(delete("/api/memory/missing"))
			.andExpect(status().isNotFound());
	}

	private MemoryRecord record(String id) {
		MemoryRecord record = new MemoryRecord();
		record.setId(id);
		record.setProjectId("project-a");
		record.setType(MemoryType.PROJECT_RULE);
		record.setKey("rule-1");
		record.setContent("keep API stable");
		record.setCreatedAt(Instant.parse("2026-08-01T00:00:00Z"));
		record.setUpdatedAt(Instant.parse("2026-08-01T00:00:00Z"));
		return record;
	}
}
