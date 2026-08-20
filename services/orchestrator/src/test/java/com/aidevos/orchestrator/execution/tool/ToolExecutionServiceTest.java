package com.aidevos.orchestrator.execution.tool;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import com.aidevos.orchestrator.execution.FailureClass;
import com.aidevos.orchestrator.execution.FailureClassifier;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolExecutionServiceTest {

	private final CommandExecutor commandExecutor = mock(CommandExecutor.class);
	private final HttpClient httpClient = mock(HttpClient.class);
	private final ToolExecutionService service = new ToolExecutionService(commandExecutor,
		new FailureClassifier(), httpClient);

	@Test
	void gitRequestRunsThroughCommandExecutorAndReportsSuccess() {
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(true);
		commandResult.setExitCode(0);
		commandResult.setOutput("On branch main");
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);

		ToolExecutionResult result = service.execute(ToolExecutionRequest.of(
			DeterministicTool.GIT, List.of("status"), "/tmp/workspace"));

		assertTrue(result.success());
		assertEquals(DeterministicTool.GIT, result.tool());
		assertEquals("On branch main", result.output());
		assertNull(result.failureClass());
		verify(commandExecutor).execute(any(CommandOptions.class));
	}

	@Test
	void failedGitCommandIsClassifiedWithoutLlm() {
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(false);
		commandResult.setExitCode(1);
		commandResult.setError("fatal: your local changes would be overwritten");
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);

		ToolExecutionResult result = service.execute(ToolExecutionRequest.of(
			DeterministicTool.GIT, List.of("merge"), null));

		assertFalse(result.success());
		assertEquals(1, result.exitCode());
		assertEquals(FailureClass.GIT_CONFLICT, result.failureClass());
	}

	@Test
	void mavenCommandIsPrefixedWithMvn() {
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(true);
		commandResult.setExitCode(0);
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);

		service.execute(ToolExecutionRequest.of(DeterministicTool.MAVEN,
			List.of("test", "-Dtest=GitStepTest"), null));

		org.mockito.ArgumentCaptor<CommandOptions> captor =
			org.mockito.ArgumentCaptor.forClass(CommandOptions.class);
		verify(commandExecutor).execute(captor.capture());
		assertEquals(List.of("mvn", "test", "-Dtest=GitStepTest"),
			captor.getValue().getCommand());
	}

	@Test
	void httpHealth200IsSuccess() throws Exception {
		HttpResponse<String> response = response(200, "ok");
		stubSend(response);

		ToolExecutionResult result = service.execute(ToolExecutionRequest.of(
			DeterministicTool.HTTP_HEALTH, List.of("http://localhost:8080/health"), null));

		assertTrue(result.success());
		assertEquals(200, result.exitCode());
		assertNull(result.failureClass());
	}

	@Test
	void httpHealth500IsHealthCheckFailed() throws Exception {
		HttpResponse<String> response = response(503, "unavailable");
		stubSend(response);

		ToolExecutionResult result = service.execute(ToolExecutionRequest.of(
			DeterministicTool.HTTP_HEALTH, List.of("http://localhost:8080/health"), null));

		assertFalse(result.success());
		assertEquals(FailureClass.HEALTH_CHECK_FAILED, result.failureClass());
	}

	@Test
	void httpHealthWithoutUrlFailsClosedWithoutHttpCall() throws Exception {
		ToolExecutionResult result = service.execute(ToolExecutionRequest.of(
			DeterministicTool.HTTP_HEALTH, List.of(), null));

		assertFalse(result.success());
		assertEquals(FailureClass.HEALTH_CHECK_FAILED, result.failureClass());
		verify(httpClient, never()).send(any(HttpRequest.class), any());
	}

	@Test
	void httpHealthExceptionIsHealthCheckFailed() throws Exception {
		when(httpClient.send(any(HttpRequest.class), any()))
			.thenThrow(new java.io.IOException("Connection refused"));
		ToolExecutionRequest request = new ToolExecutionRequest(DeterministicTool.HTTP_HEALTH,
			List.of("http://localhost:8080/health"), null, Duration.ofSeconds(5), null);

		ToolExecutionResult result = service.execute(request);

		assertFalse(result.success());
		assertEquals(FailureClass.HEALTH_CHECK_FAILED, result.failureClass());
	}

	@SuppressWarnings("unchecked")
	private HttpResponse<String> response(int statusCode, String body) {
		HttpResponse<String> response = mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(statusCode);
		when(response.body()).thenReturn(body);
		return response;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void stubSend(HttpResponse<String> response) throws Exception {
		when(httpClient.send(any(HttpRequest.class), any())).thenReturn((HttpResponse) response);
	}
}
