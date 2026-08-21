package com.aidevos.orchestrator.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SystemControllerTest {

	@Test
	void versionReturnsProductNameAndVersion() throws Exception {
		MockMvc mockMvc = standaloneSetup(new SystemController()).build();

		mockMvc.perform(get("/api/system/version"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("AI Dev OS"))
			.andExpect(jsonPath("$.version").value("v1"));
	}
}
