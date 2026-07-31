package com.aidevos.orchestrator.openclaw.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.aidevos.orchestrator.openclaw.config.OpenClawProperties;
import com.aidevos.orchestrator.openclaw.model.GatewayRequest;
import com.aidevos.orchestrator.openclaw.model.GatewayResponse;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClawWebSocketClientTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private MockWebServer server;

	private OpenClawWebSocketClient client;

	private WebSocket serverWebSocket;

	@BeforeEach
	void setUp() throws Exception {
		server = new MockWebServer();
		server.start();
	}

	@AfterEach
	void tearDown() throws Exception {
		if (serverWebSocket != null) {
			serverWebSocket.close(1000, "test complete");
		}
		if (client != null) {
			client.close();
		}
		server.shutdown();
	}

	@Test
	void shouldCompleteChallengeConnectHelloOkHandshake() throws Exception {
		server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
			@Override
			public void onOpen(WebSocket webSocket, Response response) {
				serverWebSocket = webSocket;
				webSocket.send(
						"""
							{"type":"event","event":"connect.challenge","payload":{"nonce":"nonce-1","ts":1}}
							""");
			}

			@Override
			public void onMessage(WebSocket webSocket, String text) {
				try {
					JsonNode request = objectMapper.readTree(text);
					assertEquals("connect", request.get("method").asText());
					assertEquals(4, request.get("params").get("minProtocol").asInt());
					assertEquals(4, request.get("params").get("maxProtocol").asInt());
					assertEquals("operator", request.get("params").get("role").asText());
					assertEquals("test-token", request.get("params").get("auth").get("token").asText());
					assertTrue(request.get("params").get("scopes").toString().contains("operator.read"));
					webSocket.send("""
						{"type":"res","id":"%s","ok":true,"payload":{"type":"hello-ok","protocol":4}}
						""".formatted(request.get("id").asText()));
				}
				catch (Exception error) {
					throw new AssertionError(error);
				}
			}
		}));

		client = createClient();

		client.connect().get(5, TimeUnit.SECONDS);

		assertTrue(client.isConnected());
		assertEquals("/",
				server.takeRequest(5, TimeUnit.SECONDS).getRequestUrl().encodedPath());
	}

	@Test
	void shouldAssociateRequestWithResponseById() throws Exception {
		server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
			@Override
			public void onOpen(WebSocket webSocket, Response response) {
				serverWebSocket = webSocket;
				webSocket.send(
						"""
							{"type":"event","event":"connect.challenge","payload":{"nonce":"nonce-1","ts":1}}
							""");
			}

			@Override
			public void onMessage(WebSocket webSocket, String text) {
				try {
					JsonNode request = objectMapper.readTree(text);
					if ("connect".equals(request.get("method").asText())) {
						webSocket.send("""
							{"type":"res","id":"%s","ok":true,"payload":{"type":"hello-ok","protocol":4}}
							""".formatted(request.get("id").asText()));
					}
					else {
						webSocket.send("""
							{"type":"res","id":"%s","ok":true,"payload":{"status":"ready"}}
							""".formatted(request.get("id").asText()));
					}
				}
				catch (Exception error) {
					throw new AssertionError(error);
				}
			}
		}));
		client = createClient();
		client.connect().get(5, TimeUnit.SECONDS);

		GatewayResponse response = client.send(
				new GatewayRequest("request-42", "test.read", Map.of()))
			.get(5, TimeUnit.SECONDS);

		assertEquals("request-42", response.id());
		assertEquals("ready", response.payload().get("status"));
	}

	@Test
	void shouldGenerateUniqueRequestIdsAndPreserveMethodAndParams() throws Exception {
		List<JsonNode> requests = new CopyOnWriteArrayList<>();
		enqueueGateway(requests, true);
		client = createClient();
		client.connect().get(5, TimeUnit.SECONDS);

		GatewayResponse first = client.request("test.read", Map.of("detail", true))
			.get(5, TimeUnit.SECONDS);
		GatewayResponse second = client.request("test.write", Map.of("value", "updated"))
			.get(5, TimeUnit.SECONDS);

		assertNotEquals(first.id(), second.id());
		assertEquals("test.read", requests.get(1).get("method").asText());
		assertTrue(requests.get(1).get("params").get("detail").asBoolean());
		assertEquals("test.write", requests.get(2).get("method").asText());
		assertEquals("updated", requests.get(2).get("params").get("value").asText());
	}

	@Test
	void shouldPreserveGatewayErrorFields() throws Exception {
		server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
			@Override
			public void onOpen(WebSocket webSocket, Response response) {
				serverWebSocket = webSocket;
				webSocket.send(
						"""
							{"type":"event","event":"connect.challenge","payload":{"nonce":"nonce-1","ts":1}}
							""");
			}

			@Override
			public void onMessage(WebSocket webSocket, String text) {
				try {
					JsonNode request = objectMapper.readTree(text);
					if ("connect".equals(request.get("method").asText())) {
						webSocket.send("""
							{"type":"res","id":"%s","ok":true,"payload":{"type":"hello-ok","protocol":4}}
							""".formatted(request.get("id").asText()));
					}
					else {
						webSocket.send("""
							{"type":"res","id":"%s","ok":false,"error":{"code":"INVALID_REQUEST","message":"invalid params","details":{"field":"method"}}}
							""".formatted(request.get("id").asText()));
					}
				}
				catch (Exception error) {
					throw new AssertionError(error);
				}
			}
		}));
		client = createClient();
		client.connect().get(5, TimeUnit.SECONDS);

		ExecutionException executionException = org.junit.jupiter.api.Assertions.assertThrows(
				ExecutionException.class,
				() -> client.request("test.invalid", Map.of()).get(5, TimeUnit.SECONDS));
		OpenClawGatewayException gatewayException = assertInstanceOf(
				OpenClawGatewayException.class, executionException.getCause());
		assertEquals("INVALID_REQUEST", gatewayException.getCode());
		assertEquals("invalid params", gatewayException.getGatewayMessage());
		assertEquals(Map.of("field", "method"), gatewayException.getDetails());
		assertEquals("INVALID_REQUEST", gatewayException.getGatewayError().get("code"));
	}

	@Test
	void shouldTimeoutRpcAndIgnoreLateResponse() throws Exception {
		List<JsonNode> requests = new CopyOnWriteArrayList<>();
		enqueueGateway(requests, false);
		client = createClient(Duration.ofMillis(50));
		client.connect().get(5, TimeUnit.SECONDS);

		ExecutionException executionException = org.junit.jupiter.api.Assertions.assertThrows(
				ExecutionException.class,
				() -> client.request("test.slow", Map.of()).get(5, TimeUnit.SECONDS));
		assertInstanceOf(TimeoutException.class, executionException.getCause());

		JsonNode timedOutRequest = requests.get(1);
		serverWebSocket.send("""
			{"type":"res","id":"%s","ok":true,"payload":{"status":"late"}}
			""".formatted(timedOutRequest.get("id").asText()));
		GatewayResponse nextResponse = client.request("test.next", Map.of()).get(5, TimeUnit.SECONDS);

		assertEquals("ready", nextResponse.payload().get("status"));
		assertNotEquals(timedOutRequest.get("id").asText(), nextResponse.id());
	}

	private void enqueueGateway(List<JsonNode> requests, boolean respondToEveryRequest) {
		server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
			@Override
			public void onOpen(WebSocket webSocket, Response response) {
				serverWebSocket = webSocket;
				webSocket.send(
						"""
							{"type":"event","event":"connect.challenge","payload":{"nonce":"nonce-1","ts":1}}
							""");
			}

			@Override
			public void onMessage(WebSocket webSocket, String text) {
				try {
					JsonNode request = objectMapper.readTree(text);
					requests.add(request);
					if ("connect".equals(request.get("method").asText())) {
						webSocket.send("""
							{"type":"res","id":"%s","ok":true,"payload":{"type":"hello-ok","protocol":4}}
							""".formatted(request.get("id").asText()));
					}
					else if (respondToEveryRequest || "test.next".equals(request.get("method").asText())) {
						webSocket.send("""
							{"type":"res","id":"%s","ok":true,"payload":{"status":"ready"}}
							""".formatted(request.get("id").asText()));
					}
				}
				catch (Exception error) {
					throw new AssertionError(error);
				}
			}
		}));
	}

	private OpenClawWebSocketClient createClient() {
		return createClient(Duration.ofSeconds(30));
	}

	private OpenClawWebSocketClient createClient(Duration requestTimeout) {
		OpenClawProperties properties = new OpenClawProperties();
		properties.setGatewayUrl(server.url("/").toString().replaceFirst("^http", "ws"));
		properties.setToken("test-token");
		properties.setRequestTimeout(requestTimeout);
		return new OpenClawWebSocketClient(properties, objectMapper);
	}
}
