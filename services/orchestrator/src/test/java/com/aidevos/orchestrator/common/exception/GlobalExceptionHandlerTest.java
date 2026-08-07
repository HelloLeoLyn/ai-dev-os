package com.aidevos.orchestrator.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GlobalExceptionHandlerTest {

	private final MockMvc mockMvc = standaloneSetup(new TestApi())
		.setControllerAdvice(new GlobalExceptionHandler())
		.build();

	@Test
	void shouldMapIllegalArgumentTo400WithUnifiedBody() throws Exception {
		mockMvc.perform(get("/api/test/illegal"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ILLEGAL_ARGUMENT"))
			.andExpect(jsonPath("$.message").value("bad input"))
			.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

	@Test
	void shouldMapResourceNotFoundTo404WithUnifiedBody() throws Exception {
		mockMvc.perform(get("/api/test/not-found"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.message").value("Resource not found: r-1"))
			.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

	@Test
	void shouldMapUnexpectedRuntimeExceptionTo500() throws Exception {
		mockMvc.perform(get("/api/test/runtime"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.message").value("Unexpected server error"))
			.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

	@Test
	void shouldKeepSpringNotFoundForUnknownRoute() throws Exception {
		mockMvc.perform(get("/api/unknown-route"))
			.andExpect(status().isNotFound());
	}

	@RestController
	static class TestApi {

		@GetMapping("/api/test/illegal")
		public ResponseEntity<String> illegal() {
			throw new IllegalArgumentException("bad input");
		}

		@GetMapping("/api/test/not-found")
		public ResponseEntity<String> notFound() {
			throw new ResourceNotFoundException("Resource", "r-1");
		}

		@GetMapping("/api/test/runtime")
		public ResponseEntity<String> runtime() {
			throw new IllegalStateException("boom");
		}
	}
}
