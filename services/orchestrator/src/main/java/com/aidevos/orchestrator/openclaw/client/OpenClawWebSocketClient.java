package com.aidevos.orchestrator.openclaw.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.aidevos.orchestrator.openclaw.config.OpenClawProperties;
import com.aidevos.orchestrator.openclaw.model.GatewayEvent;
import com.aidevos.orchestrator.openclaw.model.GatewayRequest;
import com.aidevos.orchestrator.openclaw.model.GatewayResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OpenClawWebSocketClient implements OpenClawClient, WebSocket.Listener {

	private static final int PROTOCOL_VERSION = 4;

	private final OpenClawProperties properties;

	private final ObjectMapper objectMapper;

	private final HttpClient httpClient;

	private final Map<String, CompletableFuture<GatewayResponse>> pendingRequests = new ConcurrentHashMap<>();

	private final List<Consumer<GatewayEvent>> eventListeners = new CopyOnWriteArrayList<>();

	private final StringBuilder textBuffer = new StringBuilder();

	private volatile WebSocket webSocket;

	private volatile CompletableFuture<Void> connectFuture;

	private volatile String connectRequestId;

	@Autowired
	public OpenClawWebSocketClient(OpenClawProperties properties, ObjectMapper objectMapper) {
		this(properties, objectMapper, HttpClient.newBuilder()
			.connectTimeout(properties.getConnectTimeout())
			.build());
	}

	OpenClawWebSocketClient(OpenClawProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.httpClient = httpClient;
	}

	@Override
	public synchronized CompletableFuture<Void> connect() {
		if (isConnected()) {
			return CompletableFuture.completedFuture(null);
		}
		if (connectFuture != null && !connectFuture.isDone()) {
			return connectFuture;
		}

		connectFuture = new CompletableFuture<>();
		httpClient.newWebSocketBuilder()
			.connectTimeout(properties.getConnectTimeout())
			.buildAsync(URI.create(properties.getGatewayUrl()), this)
			.whenComplete((socket, error) -> {
				if (error != null) {
					failConnection(new IllegalStateException("Unable to connect to OpenClaw Gateway", error));
				}
				else {
					webSocket = socket;
				}
			});
		return connectFuture;
	}

	@Override
	public CompletableFuture<GatewayResponse> send(GatewayRequest request) {
		WebSocket socket = webSocket;
		if (socket == null || socket.isOutputClosed()) {
			return CompletableFuture.failedFuture(
					new IllegalStateException("OpenClaw Gateway WebSocket is not connected"));
		}
		if (request.id() == null || request.id().isBlank()) {
			return CompletableFuture.failedFuture(new IllegalArgumentException("Gateway request id is required"));
		}

		CompletableFuture<GatewayResponse> responseFuture = new CompletableFuture<>();
		if (pendingRequests.putIfAbsent(request.id(), responseFuture) != null) {
			return CompletableFuture.failedFuture(
					new IllegalArgumentException("Duplicate Gateway request id: " + request.id()));
		}

		try {
			String message = objectMapper.writeValueAsString(request);
			socket.sendText(message, true).whenComplete((ignored, error) -> {
				if (error != null) {
					CompletableFuture<GatewayResponse> pending = pendingRequests.remove(request.id());
					if (pending != null) {
						pending.completeExceptionally(
								new IllegalStateException("Unable to send OpenClaw Gateway request", error));
					}
				}
			});
		}
		catch (JacksonException error) {
			pendingRequests.remove(request.id());
			responseFuture.completeExceptionally(
					new IllegalArgumentException("Unable to serialize OpenClaw Gateway request", error));
		}
		return responseFuture;
	}

	@Override
	public void addEventListener(Consumer<GatewayEvent> listener) {
		eventListeners.add(listener);
	}

	@Override
	public boolean isConnected() {
		WebSocket socket = webSocket;
		return socket != null && !socket.isInputClosed() && !socket.isOutputClosed()
			&& connectFuture != null && connectFuture.isDone() && !connectFuture.isCompletedExceptionally();
	}

	@Override
	public void close() {
		WebSocket socket = webSocket;
		if (socket != null && !socket.isOutputClosed()) {
			socket.sendClose(WebSocket.NORMAL_CLOSURE, "client closing");
			socket.abort();
		}
		webSocket = null;
		failPending(new IllegalStateException("OpenClaw Gateway WebSocket closed"));
	}

	@Override
	public void onOpen(WebSocket webSocket) {
		this.webSocket = webSocket;
		webSocket.request(1);
	}

	@Override
	public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
		String message = null;
		synchronized (textBuffer) {
			textBuffer.append(data);
			if (last) {
				message = textBuffer.toString();
				textBuffer.setLength(0);
			}
		}
		if (message != null) {
			handleMessage(message);
		}
		webSocket.request(1);
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
		webSocket.request(1);
		return webSocket.sendPong(message);
	}

	@Override
	public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		this.webSocket = null;
		IllegalStateException error = new IllegalStateException(
				"OpenClaw Gateway WebSocket closed with status " + statusCode);
		failConnection(error);
		failPending(error);
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public void onError(WebSocket webSocket, Throwable error) {
		this.webSocket = null;
		IllegalStateException connectionError = new IllegalStateException(
				"OpenClaw Gateway WebSocket transport failed", error);
		failConnection(connectionError);
		failPending(connectionError);
	}

	private void handleMessage(String message) {
		try {
			JsonNode root = objectMapper.readTree(message);
			String type = root.path("type").asText();
			if ("event".equals(type)) {
				handleEvent(objectMapper.treeToValue(root, GatewayEvent.class));
			}
			else if ("res".equals(type)) {
				handleResponse(objectMapper.treeToValue(root, GatewayResponse.class));
			}
		}
		catch (JacksonException error) {
			IllegalStateException protocolError = new IllegalStateException(
					"Invalid OpenClaw Gateway message", error);
			failConnection(protocolError);
			failPending(protocolError);
		}
	}

	private void handleEvent(GatewayEvent event) {
		if ("connect.challenge".equals(event.event())) {
			sendConnectRequest();
		}
		for (Consumer<GatewayEvent> listener : eventListeners) {
			listener.accept(event);
		}
	}

	private void sendConnectRequest() {
		connectRequestId = UUID.randomUUID().toString();
		Map<String, Object> client = Map.of(
				"id", "ai-dev-os-orchestrator",
				"version", "0.0.1",
				"platform", System.getProperty("os.name", "unknown").toLowerCase(),
				"mode", "operator");
		Map<String, Object> params = Map.ofEntries(
				Map.entry("minProtocol", PROTOCOL_VERSION),
				Map.entry("maxProtocol", PROTOCOL_VERSION),
				Map.entry("client", client),
				Map.entry("role", "operator"),
				Map.entry("scopes", List.of("operator.read", "operator.write")),
				Map.entry("caps", List.of()),
				Map.entry("commands", List.of()),
				Map.entry("permissions", Map.of()),
				Map.entry("auth", Map.of("token", properties.getToken())),
				Map.entry("locale", "en-US"),
				Map.entry("userAgent", "ai-dev-os-orchestrator/0.0.1"));
		send(new GatewayRequest(connectRequestId, "connect", params));
	}

	private void handleResponse(GatewayResponse response) {
		CompletableFuture<GatewayResponse> pending = pendingRequests.remove(response.id());
		if (pending != null) {
			if (response.ok()) {
				pending.complete(response);
			}
			else {
				pending.completeExceptionally(new IllegalStateException("OpenClaw Gateway request failed"));
			}
		}

		if (response.id() != null && response.id().equals(connectRequestId)) {
			if (response.ok() && response.payload() != null
					&& "hello-ok".equals(response.payload().get("type"))) {
				connectFuture.complete(null);
			}
			else {
				failConnection(new IllegalStateException("OpenClaw Gateway handshake failed"));
			}
		}
	}

	private void failConnection(Throwable error) {
		CompletableFuture<Void> future = connectFuture;
		if (future != null && !future.isDone()) {
			future.completeExceptionally(error);
		}
	}

	private void failPending(Throwable error) {
		pendingRequests.forEach((id, future) -> future.completeExceptionally(error));
		pendingRequests.clear();
	}
}
