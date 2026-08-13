package com.aidevos.orchestrator.controller;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.validation.ValidationDecision;
import com.aidevos.orchestrator.validation.ValidationEvidenceService;
import com.aidevos.orchestrator.validation.ValidationRun;
import com.aidevos.orchestrator.validation.ValidationService;
import com.aidevos.orchestrator.validation.ValidationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ValidationControllerTest {
	@Test void startsAndQueriesValidation() throws Exception {
		ValidationService service = mock(ValidationService.class);
		ValidationRun run = run();
		when(service.start("task-1")).thenReturn(run);
		when(service.findByTask("task-1")).thenReturn(List.of(run));
		when(service.get("validation-1")).thenReturn(run);
		MockMvc mvc = mvc(service);
		mvc.perform(post("/api/tasks/task-1/validations")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.decision").value("PASS"));
		mvc.perform(get("/api/tasks/task-1/validations")).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].taskId").value("task-1"));
		mvc.perform(get("/api/validations/validation-1")).andExpect(status().isOk())
			.andExpect(jsonPath("$.validationRunId").value("validation-1"));
	}

	@Test void missingTaskAndInvalidValidationReturn404() throws Exception {
		ValidationService service = mock(ValidationService.class);
		when(service.start("missing")).thenThrow(new ResourceNotFoundException("Task", "missing"));
		when(service.get("invalid")).thenThrow(new ResourceNotFoundException("ValidationRun", "invalid"));
		MockMvc mvc = mvc(service);
		mvc.perform(post("/api/tasks/missing/validations")).andExpect(status().isNotFound());
		mvc.perform(get("/api/validations/invalid")).andExpect(status().isNotFound());
	}

	@Test void ownershipMismatchIsRejected() throws Exception {
		ValidationService service = mock(ValidationService.class);
		when(service.start("task-1")).thenThrow(new IllegalArgumentException(
			"Task workspace does not belong to project"));
		mvc(service).perform(post("/api/tasks/task-1/validations")).andExpect(status().isBadRequest());
	}

	private MockMvc mvc(ValidationService service) {
		return standaloneSetup(new ValidationController(service, mock(ValidationEvidenceService.class)))
			.setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	private ValidationRun run() {
		ValidationRun run = new ValidationRun("validation-1", "task-1", "project-1",
			"workspace-1", null, null); run.setStartedAt(Instant.now()); run.setCompletedAt(Instant.now());
		run.setStatus(ValidationStatus.SUCCESS); run.setDecision(ValidationDecision.PASS); return run;
	}
}
