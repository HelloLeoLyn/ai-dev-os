package com.aidevos.orchestrator.openclaw.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.aidevos.orchestrator.openclaw.client.OpenClawClient;
import com.aidevos.orchestrator.openclaw.model.GatewayResponse;
import com.aidevos.orchestrator.openclaw.model.OpenClawTaskRequest;
import com.aidevos.orchestrator.openclaw.model.OpenClawTaskResult;
import org.springframework.stereotype.Service;

@Service
public class OpenClawTaskService {

	private static final String STATUS_OK = "ok";

	private static final String STATUS_ERROR = "error";

	private static final String STATUS_TIMEOUT = "timeout";

	private static final String STATUS_PENDING = "pending";

	private final OpenClawClient openClawClient;

	public OpenClawTaskService(OpenClawClient openClawClient) {
		this.openClawClient = openClawClient;
	}

	public CompletableFuture<OpenClawTaskResult> execute(OpenClawTaskRequest request) {
		validateRequest(request);
		Map<String, Object> agentParams = Map.of(
				"agentId", request.agentId(),
				"message", request.message());

		return openClawClient.request("agent", agentParams)
			.thenCompose(agentResponse -> waitForAgent(agentResponse));
	}

	private CompletableFuture<OpenClawTaskResult> waitForAgent(GatewayResponse agentResponse) {
		String runId = requiredString(agentResponse.payload(), "runId", "agent");
		String sessionKey = requiredString(agentResponse.payload(), "sessionKey", "agent");

		return openClawClient.request("agent.wait", Map.of("runId", runId))
			.thenCompose(waitResponse -> handleWaitResponse(runId, sessionKey, waitResponse));
	}

	private CompletableFuture<OpenClawTaskResult> handleWaitResponse(String runId, String sessionKey,
			GatewayResponse waitResponse) {
		String status = requiredString(waitResponse.payload(), "status", "agent.wait");
		if (STATUS_OK.equals(status)) {
			return loadHistory(runId, sessionKey);
		}
		if (STATUS_ERROR.equals(status) || STATUS_TIMEOUT.equals(status) || STATUS_PENDING.equals(status)) {
			return CompletableFuture.completedFuture(new OpenClawTaskResult(runId, sessionKey, status, null));
		}
		return CompletableFuture.failedFuture(
				new IllegalStateException("Unknown agent.wait status: " + status));
	}

	private CompletableFuture<OpenClawTaskResult> loadHistory(String runId, String sessionKey) {
		return openClawClient.request("chat.history", Map.of("sessionKey", sessionKey))
			.thenApply(historyResponse -> new OpenClawTaskResult(
					runId, sessionKey, STATUS_OK, parseHistoryOutput(historyResponse.payload())));
	}

	private String parseHistoryOutput(Map<String, Object> payload) {
		if (payload == null || !(payload.get("messages") instanceof List<?> messages)) {
			throw new IllegalStateException("chat.history response is missing messages");
		}

		StringBuilder output = new StringBuilder();
		for (Object item : messages) {
			if (!(item instanceof Map<?, ?> message) || !"assistant".equals(message.get("role"))) {
				continue;
			}
			Object content = message.get("content");
			if (content instanceof String text && !text.isBlank()) {
				if (!output.isEmpty()) {
					output.append(System.lineSeparator());
				}
				output.append(text);
			}
		}
		return output.toString();
	}

	private String requiredString(Map<String, Object> payload, String field, String method) {
		if (payload != null && payload.get(field) instanceof String value && !value.isBlank()) {
			return value;
		}
		throw new IllegalStateException(method + " response is missing " + field);
	}

	private void validateRequest(OpenClawTaskRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("OpenClaw task request is required");
		}
		if (request.agentId() == null || request.agentId().isBlank()) {
			throw new IllegalArgumentException("OpenClaw agentId is required");
		}
		if (request.message() == null || request.message().isBlank()) {
			throw new IllegalArgumentException("OpenClaw message is required");
		}
	}
}
