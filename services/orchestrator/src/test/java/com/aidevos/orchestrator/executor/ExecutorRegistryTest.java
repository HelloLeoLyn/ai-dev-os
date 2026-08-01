package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.openclaw.service.OpenClawTaskService;
import com.aidevos.orchestrator.browser.BrowserResultMapper;
import com.aidevos.orchestrator.browser.BrowserTaskPromptBuilder;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ExecutorRegistryTest {

	@Test
	void shouldRegisterMockExecutor() {
		MockAgentExecutor mockAgentExecutor = new MockAgentExecutor();
		ExecutorRegistry executorRegistry = new ExecutorRegistry(List.of(mockAgentExecutor));

		assertSame(mockAgentExecutor, executorRegistry.get("mock"));
	}

	@Test
	void shouldRegisterCodexExecutor() {
		CodexExecutor codexExecutor = new CodexExecutor(mock(CommandExecutor.class));
		ExecutorRegistry executorRegistry = new ExecutorRegistry(List.of(codexExecutor));

		assertSame(codexExecutor, executorRegistry.get("codex"));
	}

	@Test
	void shouldRegisterOpenClawExecutor() {
		ObjectMapper objectMapper = new ObjectMapper();
		OpenClawExecutor openClawExecutor = new OpenClawExecutor(mock(OpenClawTaskService.class),
			new BrowserTaskPromptBuilder(objectMapper), new BrowserResultMapper(objectMapper));
		ExecutorRegistry executorRegistry = new ExecutorRegistry(List.of(openClawExecutor));

		assertSame(openClawExecutor, executorRegistry.get("openclaw"));
	}

	@Test
	void shouldReturnNullForUnknownType() {
		ExecutorRegistry executorRegistry = new ExecutorRegistry(List.of());

		assertNull(executorRegistry.get("unknown"));
	}

	@Test
	void shouldFailWhenTypeIsAlreadyRegistered() {
		MockAgentExecutor firstExecutor = new MockAgentExecutor();
		MockAgentExecutor secondExecutor = new MockAgentExecutor();

		assertThrows(IllegalStateException.class,
			() -> new ExecutorRegistry(List.of(firstExecutor, secondExecutor)));
	}
}
