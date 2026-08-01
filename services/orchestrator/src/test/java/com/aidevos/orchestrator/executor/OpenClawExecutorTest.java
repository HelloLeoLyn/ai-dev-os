package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.openclaw.model.OpenClawTaskRequest;
import com.aidevos.orchestrator.openclaw.model.OpenClawTaskResult;
import com.aidevos.orchestrator.openclaw.service.OpenClawTaskService;

import java.util.concurrent.CompletableFuture;

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
		assertEquals("openclaw", new OpenClawExecutor(mock(OpenClawTaskService.class)).getType());
	}

	@Test
	void shouldConvertContextAndMapSuccessfulTaskResult() {
		OpenClawTaskService taskService = mock(OpenClawTaskService.class);
		when(taskService.execute(new OpenClawTaskRequest("planner", "Implement feature")))
			.thenReturn(CompletableFuture.completedFuture(
					new OpenClawTaskResult("run-1", "session-1", "ok", "Feature implemented")));

		ExecutionContext context = new ExecutionContext();
		context.setAgentName("browser-agent");
		context.setExternalAgentId("planner");
		context.setInput("Implement feature");
		context.setWorkspace("/workspace/project");

		ExecutionResult result = new OpenClawExecutor(taskService).execute(context);

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
		context.setExternalAgentId("coder");
		context.setInput("Run task");

		ExecutionResult result = new OpenClawExecutor(taskService).execute(context);

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
		context.setExternalAgentId("coder");
		context.setInput("Run slow task");

		ExecutionResult result = new OpenClawExecutor(taskService).execute(context);

		assertFalse(result.isSuccess());
		assertEquals("OpenClaw task failed: timeout", result.getMessage());
		assertNull(result.getOutput());
	}
}
