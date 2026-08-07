package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;

import java.util.List;

import com.aidevos.orchestrator.schedule.ScheduleService;
import com.aidevos.orchestrator.schedule.ScheduledTask;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleControllerTest {

	@Test
	void shouldCreateSchedule() throws Exception {
		ScheduleService service = mock(ScheduleService.class);
		when(service.register(any(ScheduledTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

		mockMvc(service).perform(post("/api/schedules")
				.contentType(MediaType.APPLICATION_JSON)
				.content(scheduleJson(true)))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/api/schedules/schedule-1"))
			.andExpect(jsonPath("$.id").value("schedule-1"))
			.andExpect(jsonPath("$.enabled").value(true));
	}

	@Test
	void shouldQuerySchedulesIncludingDisabledSchedule() throws Exception {
		ScheduleService service = mock(ScheduleService.class);
		ScheduledTask disabled = scheduledTask(false);
		when(service.getAll()).thenReturn(List.of(disabled));

		mockMvc(service).perform(get("/api/schedules"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value("schedule-1"))
			.andExpect(jsonPath("$[0].enabled").value(false));
	}

	@Test
	void shouldDeleteSchedule() throws Exception {
		ScheduleService service = mock(ScheduleService.class);
		when(service.remove("schedule-1")).thenReturn(true);

		mockMvc(service).perform(delete("/api/schedules/schedule-1"))
			.andExpect(status().isNoContent());

		verify(service).remove("schedule-1");
	}

	@Test
	void shouldRejectInvalidCron() throws Exception {
		ScheduleService service = mock(ScheduleService.class);
		when(service.register(any(ScheduledTask.class)))
			.thenThrow(new IllegalArgumentException("invalid cron"));

		mockMvc(service).perform(post("/api/schedules")
				.contentType(MediaType.APPLICATION_JSON)
				.content(scheduleJson(true)))
			.andExpect(status().isBadRequest());
	}

	private MockMvc mockMvc(ScheduleService service) {
		return standaloneSetup(new ScheduleController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	private ScheduledTask scheduledTask(boolean enabled) {
		ScheduledTask scheduledTask = new ScheduledTask();
		scheduledTask.setId("schedule-1");
		scheduledTask.setTaskId("task-1");
		scheduledTask.setCron("0 */5 * * * *");
		scheduledTask.setEnabled(enabled);
		scheduledTask.setZoneId("Asia/Shanghai");
		return scheduledTask;
	}

	private String scheduleJson(boolean enabled) {
		return """
			{
			  "id": "schedule-1",
			  "taskId": "task-1",
			  "cron": "0 */5 * * * *",
			  "enabled": %s,
			  "zoneId": "Asia/Shanghai"
			}
			""".formatted(enabled);
	}
}
