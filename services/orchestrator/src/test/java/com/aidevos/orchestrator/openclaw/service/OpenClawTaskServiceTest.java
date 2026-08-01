package com.aidevos.orchestrator.openclaw.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.aidevos.orchestrator.openclaw.client.OpenClawClient;
import com.aidevos.orchestrator.openclaw.config.OpenClawProperties;
import com.aidevos.orchestrator.openclaw.model.GatewayResponse;
import com.aidevos.orchestrator.openclaw.model.OpenClawTaskRequest;
import com.aidevos.orchestrator.openclaw.model.OpenClawTaskResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenClawTaskServiceTest {

	private OpenClawClient openClawClient;

	private OpenClawTaskService service;

	@BeforeEach
	void setUp() {
		openClawClient = mock(OpenClawClient.class);
		when(openClawClient.isConnected()).thenReturn(true);
		OpenClawProperties properties = new OpenClawProperties();
		properties.setAgentWaitTimeout(java.time.Duration.ofSeconds(90));
		service = new OpenClawTaskService(openClawClient, properties);
	}

	@Test
	void shouldConnectBeforeRequestWhenDisconnected() {
		when(openClawClient.isConnected()).thenReturn(false, true);
		when(openClawClient.connect()).thenReturn(CompletableFuture.completedFuture(null));
		when(openClawClient.request(eq("agent"), anyMap()))
			.thenReturn(response(Map.of("runId", "run-42", "sessionKey", "session-7")));
		when(openClawClient.request(eq("agent.wait"), anyMap()))
			.thenReturn(response(Map.of("status", "pending")));

		service.execute(new OpenClawTaskRequest("planner", "Implement task")).join();

		var order = inOrder(openClawClient);
		order.verify(openClawClient).isConnected();
		order.verify(openClawClient).connect();
		order.verify(openClawClient).request(eq("agent"), anyMap());
	}

	@Test
	void shouldNotConnectWhenAlreadyConnected() {
		when(openClawClient.request(eq("agent"), anyMap()))
			.thenReturn(response(Map.of("runId", "run-42", "sessionKey", "session-7")));
		when(openClawClient.request(eq("agent.wait"), anyMap()))
			.thenReturn(response(Map.of("status", "pending")));

		service.execute(new OpenClawTaskRequest("planner", "Implement task")).join();

		verify(openClawClient, never()).connect();
	}

	@Test
	void shouldExecuteAgentWaitAndParseChatHistory() {
		when(openClawClient.request(eq("agent"), anyMap()))
			.thenReturn(response(Map.of("runId", "run-42", "sessionKey", "session-7")));
		when(openClawClient.request(eq("agent.wait"), anyMap()))
			.thenReturn(response(Map.of("status", "ok")));
		when(openClawClient.request(eq("chat.history"), anyMap()))
			.thenReturn(response(Map.of("messages", List.of(
					Map.of("role", "user", "content", "ignored"),
					Map.of("role", "assistant", "content", "old answer"),
					Map.of("role", "assistant", "content", List.of(
						Map.of("type", "thinking", "thinking", "ignored"),
						Map.of("type", "text", "text", "first line"),
						Map.of("type", "text", "text", "second line")))))));

		OpenClawTaskResult result = service.execute(new OpenClawTaskRequest("planner", "Implement task"))
			.join();

		ArgumentCaptor<Map<String, Object>> agentParams = mapCaptor();
		verify(openClawClient).request(eq("agent"), agentParams.capture());
		assertEquals("planner", agentParams.getValue().get("agentId"));
		assertEquals("Implement task", agentParams.getValue().get("message"));
		UUID.fromString((String) agentParams.getValue().get("idempotencyKey"));
		assertFalse(agentParams.getValue().containsKey("cwd"));
		assertFalse(agentParams.getValue().containsKey("workspace"));

		verify(openClawClient).request("agent.wait", Map.of(
			"runId", "run-42", "timeoutMs", 90_000L));
		verify(openClawClient).request("chat.history", Map.of("sessionKey", "session-7"));
		assertEquals("run-42", result.runId());
		assertEquals("session-7", result.sessionKey());
		assertEquals("ok", result.status());
		assertTrue(result.successful());
		assertEquals("first line" + System.lineSeparator() + "second line", result.output());
	}

	@Test
	void shouldFailWhenHistoryHasNoAssistantOutput() {
		when(openClawClient.request(eq("agent"), anyMap()))
			.thenReturn(response(Map.of("runId", "run-empty", "sessionKey", "session-empty")));
		when(openClawClient.request(eq("agent.wait"), anyMap()))
			.thenReturn(response(Map.of("status", "ok")));
		when(openClawClient.request(eq("chat.history"), anyMap()))
			.thenReturn(response(Map.of("messages", List.of(
				Map.of("role", "user", "content", "question")))));

		var exception = assertThrows(java.util.concurrent.CompletionException.class,
			() -> service.execute(new OpenClawTaskRequest("main", "question")).join());

		assertEquals("chat.history response has no assistant output",
			exception.getCause().getMessage());
	}

	@ParameterizedTest
	@ValueSource(strings = { "error", "timeout", "pending" })
	void shouldReturnNonSuccessfulWaitStatusWithoutLoadingHistory(String status) {
		when(openClawClient.request(eq("agent"), anyMap()))
			.thenReturn(response(Map.of("runId", "run-failed", "sessionKey", "session-failed")));
		when(openClawClient.request(eq("agent.wait"), anyMap()))
			.thenReturn(response(Map.of("status", status)));

		OpenClawTaskResult result = service.execute(new OpenClawTaskRequest("coder", "Run task")).join();

		assertEquals("run-failed", result.runId());
		assertEquals("session-failed", result.sessionKey());
		assertEquals(status, result.status());
		assertFalse(result.successful());
		assertNull(result.output());
		verify(openClawClient, never()).request(eq("chat.history"), anyMap());
	}

	@SuppressWarnings("unchecked")
	private ArgumentCaptor<Map<String, Object>> mapCaptor() {
		return ArgumentCaptor.forClass(Map.class);
	}

	private CompletableFuture<GatewayResponse> response(Map<String, Object> payload) {
		return CompletableFuture.completedFuture(
				new GatewayResponse("res", "response-id", true, payload, null));
	}
}
