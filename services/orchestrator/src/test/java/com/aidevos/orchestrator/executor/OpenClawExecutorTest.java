package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.browser.BrowserResultMapper;
import com.aidevos.orchestrator.browser.BrowserTaskPromptBuilder;
import com.aidevos.orchestrator.openclaw.model.OpenClawTaskRequest;
import com.aidevos.orchestrator.openclaw.model.OpenClawTaskResult;
import com.aidevos.orchestrator.openclaw.service.OpenClawTaskService;

import java.util.concurrent.CompletableFuture;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenClawExecutorTest {

	@Test
	void shouldReturnOpenClawType() {
		assertEquals("openclaw", executor(mock(OpenClawTaskService.class)).getType());
	}

	@Test
	void shouldConvertContextAndMapSuccessfulTaskResult() {
		OpenClawTaskService taskService = mock(OpenClawTaskService.class);
		when(taskService.execute(new OpenClawTaskRequest("planner", "Implement feature")))
			.thenReturn(CompletableFuture.completedFuture(
					new OpenClawTaskResult("run-1", "session-1", "ok", "Feature implemented")));

		ExecutionContext context = new ExecutionContext();
		context.setAgentName("browser-agent");
		context.setParameters(Map.of("agentId", "planner"));
		context.setInput("Implement feature");
		context.setWorkspace("/workspace/project");

		ExecutionResult result = executor(taskService).execute(context);

		ArgumentCaptor<OpenClawTaskRequest> requestCaptor = ArgumentCaptor.forClass(OpenClawTaskRequest.class);
		verify(taskService).execute(requestCaptor.capture());
		assertEquals("planner", requestCaptor.getValue().agentId());
		assertEquals("Implement feature", requestCaptor.getValue().message());
		assertTrue(result.isSuccess());
		assertEquals("Task executed successfully", result.getMessage());
		assertEquals("Feature implemented", result.getOutput());
	}

	@Test
	void shouldPreserveAvailableErrorInformationWhenMappingFailedTaskResult() {
		OpenClawTaskService taskService = mock(OpenClawTaskService.class);
		when(taskService.execute(new OpenClawTaskRequest("coder", "Run task")))
			.thenReturn(CompletableFuture.completedFuture(
					new OpenClawTaskResult("run-2", "session-2", "error", "Gateway rejected task")));

		ExecutionContext context = new ExecutionContext();
		context.setAgentName("coder");
		context.setParameters(Map.of("agentId", "coder"));
		context.setInput("Run task");

		ExecutionResult result = executor(taskService).execute(context);

		assertFalse(result.isSuccess());
		assertEquals("Gateway rejected task", result.getMessage());
		assertNull(result.getOutput());
	}

	@Test
	void shouldUseStatusWhenFailedTaskResultHasNoErrorInformation() {
		OpenClawTaskService taskService = mock(OpenClawTaskService.class);
		when(taskService.execute(new OpenClawTaskRequest("coder", "Run slow task")))
			.thenReturn(CompletableFuture.completedFuture(
					new OpenClawTaskResult("run-3", "session-3", "timeout", null)));

		ExecutionContext context = new ExecutionContext();
		context.setAgentName("coder");
		context.setParameters(Map.of("agentId", "coder"));
		context.setInput("Run slow task");

		ExecutionResult result = executor(taskService).execute(context);

		assertFalse(result.isSuccess());
		assertEquals("OpenClaw task failed: timeout", result.getMessage());
		assertNull(result.getOutput());
	}

	@Test
	void shouldBuildBrowserPromptAndMapScreenshotArtifact() {
		OpenClawTaskService taskService = mock(OpenClawTaskService.class);
		when(taskService.execute(org.mockito.ArgumentMatchers.argThat(request ->
			"main".equals(request.agentId())
				&& request.message().contains("\"action\":\"screenshot\"")
				&& request.message().contains("https://example.com"))))
			.thenReturn(CompletableFuture.completedFuture(new OpenClawTaskResult(
				"run-4", "session-4", "ok",
				"{\"output\":\"Captured Example Domain\",\"artifacts\":[{\"type\":\"screenshot\",\"name\":\"example.png\",\"mediaType\":\"image/png\",\"uri\":\"C:/shots/example.png\"}]}")));

		ExecutionContext context = new ExecutionContext();
		context.setAgentName("browser-agent");
		context.setParameters(Map.of(
			"agentId", "main",
			"browser", Map.of("action", "screenshot", "url", "https://example.com")));
		context.setInput("Capture the example page");

		ExecutionResult result = executor(taskService).execute(context);

		assertTrue(result.isSuccess());
		assertEquals("Captured Example Domain", result.getOutput());
		assertEquals(1, result.getArtifacts().size());
		assertEquals("screenshot", result.getArtifacts().get(0).getType());
		assertEquals("image/png", result.getArtifacts().get(0).getMediaType());
		assertEquals("C:/shots/example.png", result.getArtifacts().get(0).getUri());
	}

	@Test
	void shouldRejectUnsupportedBrowserActionBeforeCallingOpenClaw() {
		OpenClawTaskService taskService = mock(OpenClawTaskService.class);
		ExecutionContext context = new ExecutionContext();
		context.setParameters(Map.of(
			"agentId", "main",
			"browser", Map.of("action", "delete-history")));
		context.setInput("Unsupported action");

		IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
			IllegalArgumentException.class, () -> executor(taskService).execute(context));

		assertEquals("Unsupported browser action: delete-history", exception.getMessage());
	}

	private OpenClawExecutor executor(OpenClawTaskService taskService) {
		ObjectMapper objectMapper = new ObjectMapper();
		return new OpenClawExecutor(taskService, new BrowserTaskPromptBuilder(objectMapper),
			new BrowserResultMapper(objectMapper));
	}
}
