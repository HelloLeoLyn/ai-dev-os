package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;

import com.aidevos.orchestrator.modelrouter.ModelProvider;
import com.aidevos.orchestrator.modelrouter.ModelRoute;
import com.aidevos.orchestrator.modelrouter.ModelRouterService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ModelControllerTest {

	@Test
	void shouldReturnModelProviders() throws Exception {
		ModelRouterService service = mock(ModelRouterService.class);
		ModelProvider deepseek = new ModelProvider();
		deepseek.setProviderId("deepseek");
		deepseek.setName("DeepSeek");
		deepseek.setType("LLM");
		deepseek.setModel("deepseek-chat");
		deepseek.setEnabled(true);
		when(service.listProviders()).thenReturn(List.of(deepseek));
		MockMvc mockMvc = standaloneSetup(new ModelController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(get("/api/models"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].providerId").value("deepseek"))
			.andExpect(jsonPath("$[0].name").value("DeepSeek"))
			.andExpect(jsonPath("$[0].type").value("LLM"))
			.andExpect(jsonPath("$[0].model").value("deepseek-chat"))
			.andExpect(jsonPath("$[0].enabled").value(true));
	}

	@Test
	void shouldReturnRouteRules() throws Exception {
		ModelRouterService service = mock(ModelRouterService.class);
		when(service.listRoutes()).thenReturn(List.of(
			new ModelRoute("TASK_ANALYSIS", "deepseek", "deepseek-chat", true),
			new ModelRoute("GENERAL", "openai", "gpt-4o", true)));
		MockMvc mockMvc = standaloneSetup(new ModelController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(get("/api/models/routes"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].taskType").value("TASK_ANALYSIS"))
			.andExpect(jsonPath("$[0].providerId").value("deepseek"))
			.andExpect(jsonPath("$[0].model").value("deepseek-chat"))
			.andExpect(jsonPath("$[1].taskType").value("GENERAL"))
			.andExpect(jsonPath("$[1].providerId").value("openai"));
	}
}
