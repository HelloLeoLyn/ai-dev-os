package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;

import com.aidevos.orchestrator.audit.*;
import com.aidevos.orchestrator.audit.query.*;
import com.aidevos.orchestrator.audit.timeline.TimelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import static com.aidevos.orchestrator.audit.query.AuditQueryServiceTest.event;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AuditControllerTest {
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		InMemoryAuditRepository repository = new InMemoryAuditRepository();
		repository.append(event("1", EventType.JOB_STARTED, "run-1", "job-1", "exec-1",
			"2026-08-03T01:00:00Z"));
		repository.append(event("2", EventType.JOB_SUCCEEDED, "run-1", "job-1", "exec-1",
			"2026-08-03T01:00:01Z"));
		AuditController controller = new AuditController(new AuditQueryService(repository),
			new TimelineService(repository));
		mvc = standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	@Test
	void exposesAuditQueryWithoutIdempotencyKey() throws Exception {
		mvc.perform(get("/api/audit/events")
				.param("jobId", "job-1")
				.param("eventTypes", "JOB_SUCCEEDED"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.count").value(1))
			.andExpect(jsonPath("$.events[0].type").value("JOB_SUCCEEDED"))
			.andExpect(jsonPath("$.events[0].idempotencyKey").doesNotExist());
	}

	@Test
	void exposesThreeTimelineScopes() throws Exception {
		mvc.perform(get("/api/timelines/plan-runs/run-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scopeType").value("PLAN_RUN"))
			.andExpect(jsonPath("$.count").value(2));
		mvc.perform(get("/api/timelines/executions/exec-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scopeType").value("EXECUTION"));
		mvc.perform(get("/api/timelines/jobs/job-1").param("offset", "1").param("limit", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scopeType").value("JOB"))
			.andExpect(jsonPath("$.events[0].type").value("JOB_SUCCEEDED"));
	}

	@Test
	void rejectsInvalidPaginationAndTimeRange() throws Exception {
		mvc.perform(get("/api/timelines/jobs/job-1").param("limit", "0"))
			.andExpect(status().isBadRequest());
		mvc.perform(get("/api/audit/events")
				.param("occurredAfter", "2026-08-03T02:00:00Z")
				.param("occurredBefore", "2026-08-03T01:00:00Z"))
			.andExpect(status().isBadRequest());
	}
}
