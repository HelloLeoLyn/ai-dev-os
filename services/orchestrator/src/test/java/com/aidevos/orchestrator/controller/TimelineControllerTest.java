package com.aidevos.orchestrator.controller;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.timeline.TimelineEventDTO;
import com.aidevos.orchestrator.timeline.TimelineService;
import com.aidevos.orchestrator.timeline.UnifiedTimeline;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TimelineControllerTest {

	@Test
	void shouldReturnUnifiedTimeline() throws Exception {
		TimelineService service = mock(TimelineService.class);
		UnifiedTimeline timeline = new UnifiedTimeline("JOB", "job-1", List.of(
			new TimelineEventDTO("event-1", "JOB_STARTED", "JOB", "job-1",
				"RUNNING", "job started", Instant.parse("2026-08-01T00:00:00Z"))));
		when(service.timeline("job-1")).thenReturn(timeline);
		MockMvc mockMvc = standaloneSetup(new TimelineController(service)).build();

		mockMvc.perform(get("/api/timeline/job-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scopeType").value("JOB"))
			.andExpect(jsonPath("$.scopeId").value("job-1"))
			.andExpect(jsonPath("$.events[0].eventId").value("event-1"))
			.andExpect(jsonPath("$.events[0].eventType").value("JOB_STARTED"))
			.andExpect(jsonPath("$.events[0].sourceType").value("JOB"))
			.andExpect(jsonPath("$.events[0].sourceId").value("job-1"))
			.andExpect(jsonPath("$.events[0].status").value("RUNNING"))
			.andExpect(jsonPath("$.events[0].message").value("job started"))
			.andExpect(jsonPath("$.events[0].timestamp").value("2026-08-01T00:00:00Z"));

		verify(service).timeline("job-1");
	}

	@Test
	void shouldRejectBlankTimelineId() throws Exception {
		TimelineService service = mock(TimelineService.class);
		when(service.timeline(anyString())).thenThrow(new IllegalArgumentException("Timeline id is required"));
		MockMvc mockMvc = standaloneSetup(new TimelineController(service)).build();

		mockMvc.perform(get("/api/timeline/%20"))
			.andExpect(status().isBadRequest());
	}
}
